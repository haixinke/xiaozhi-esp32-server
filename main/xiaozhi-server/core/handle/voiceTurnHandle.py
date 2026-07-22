import asyncio
import json
import queue
import threading

from core.handle.abortHandle import handleAbortMessage
from core.handle.receiveAudioHandle import handleAudioMessage
from core.providers.asr.dto.dto import InterfaceType
from core.voice_turn import VoiceInputEvent, VoiceInputEventType, TurnContext


TAG = __name__
MAX_TURN_FRAMES = 600
MAX_TURN_BYTES = 2 * 1024 * 1024
MAX_FRAME_BYTES = 64 * 1024


async def send_voice_turn_ack(conn, turn_id: str, state: str) -> None:
    try:
        await conn.websocket.send(
            json.dumps({"type": "listen", "state": state, "turn_id": turn_id})
        )
    except Exception as error:
        conn.logger.bind(tag=TAG).warning(
            f"发送语音轮次确认失败: state={state}, error={type(error).__name__}"
        )


def enqueue_voice_audio(conn, audio: bytes) -> bool:
    """Route v2 audio into its ordered queue. Return True when v2 owns ingress."""
    if not conn.voice_turn_v2_enabled:
        return False

    if conn.voice_turn_ingress_state != "open":
        return True

    if (
        not isinstance(audio, bytes)
        or len(audio) > MAX_FRAME_BYTES
        or conn.voice_turn_received_frames >= MAX_TURN_FRAMES
        or conn.voice_turn_received_bytes + len(audio) > MAX_TURN_BYTES
    ):
        conn.voice_turn_ingress_state = "failed"
        asyncio.create_task(_fail_voice_turn(conn, conn.voice_turn_id))
        return True

    # Keep one slot reserved for the terminal control event.
    if conn.voice_turn_queue.qsize() >= conn.voice_turn_queue.maxsize - 1:
        conn.voice_turn_ingress_state = "failed"
        asyncio.create_task(_fail_voice_turn(conn, conn.voice_turn_id))
        return True

    try:
        conn.voice_turn_queue.put_nowait(
            VoiceInputEvent(
                VoiceInputEventType.AUDIO,
                conn.voice_turn_id,
                audio=audio,
            )
        )
        conn.voice_turn_received_frames += 1
        conn.voice_turn_received_bytes += len(audio)
    except queue.Full:
        conn.voice_turn_ingress_state = "failed"
        asyncio.create_task(_fail_voice_turn(conn, conn.voice_turn_id))
    return True


async def begin_voice_turn(conn, turn_id: str) -> bool:
    if conn.voice_turn_v2_enabled and conn.voice_turn_ingress_state not in {
        "idle",
        "terminal",
    }:
        await send_voice_turn_ack(conn, turn_id, "error")
        return False

    if not _ensure_voice_turn_consumer(conn):
        await send_voice_turn_ack(conn, turn_id, "error")
        return False
    conn.voice_turn_v2_enabled = True
    conn.voice_turn_id = turn_id
    conn.voice_turn_ingress_state = "open"
    conn.voice_turn_received_frames = 0
    conn.voice_turn_received_bytes = 0
    try:
        conn.voice_turn_queue.put_nowait(
            VoiceInputEvent(VoiceInputEventType.START, turn_id)
        )
        conn.voice_turn_done.clear()
        return True
    except queue.Full:
        conn.voice_turn_ingress_state = "terminal"
        conn.voice_turn_done.set()
        await send_voice_turn_ack(conn, turn_id, "error")
        return False


async def finish_voice_turn(conn, turn_id: str) -> bool:
    if _matches_current_turn(conn, turn_id) and conn.voice_turn_ingress_state == "terminal":
        await send_voice_turn_ack(conn, turn_id, "stopped")
        return True
    if not _matches_open_turn(conn, turn_id):
        await send_voice_turn_ack(conn, turn_id, "error")
        return False
    conn.voice_turn_ingress_state = "ending"
    await _put_control_event(
        conn, VoiceInputEvent(VoiceInputEventType.END, turn_id)
    )
    return True


async def abort_voice_turn(conn, turn_id: str) -> bool:
    if not _matches_current_turn(conn, turn_id):
        await send_voice_turn_ack(conn, turn_id, "error")
        return False
    if conn.voice_turn_ingress_state == "terminal":
        return True
    if conn.voice_turn_ingress_state == "aborting":
        return True
    conn.voice_turn_ingress_state = "aborting"
    _cancel_voice_turn_finalizer(conn)
    await _put_control_event(
        conn,
        VoiceInputEvent(
            VoiceInputEventType.ABORT,
            turn_id,
            terminal_state="cancelled",
        ),
    )
    return True


async def disable_voice_turn_v2(conn) -> None:
    if not conn.voice_turn_v2_enabled:
        return
    turn_id = conn.voice_turn_id
    if turn_id and conn.voice_turn_ingress_state not in {"idle", "terminal"}:
        conn.voice_turn_ingress_state = "aborting"
        await _put_control_event(
            conn,
            VoiceInputEvent(
                VoiceInputEventType.ABORT,
                turn_id,
                terminal_state=None,
            ),
        )
        try:
            await asyncio.wait_for(conn.voice_turn_done.wait(), timeout=2)
        except asyncio.TimeoutError:
            conn.logger.bind(tag=TAG).warning("等待语音轮次取消超时")
            await _cancel_and_wait_voice_turn_event(conn)
            _cancel_voice_turn_finalizer(conn)
            if conn.asr and conn.asr.interface_type == InterfaceType.STREAM:
                await _reset_stream_provider(conn)
            conn.reset_audio_states()
            conn.voice_turn_consumer_id = None
            while True:
                try:
                    conn.voice_turn_queue.get_nowait()
                except queue.Empty:
                    break
            conn.voice_turn_done.set()
    conn.voice_turn_v2_enabled = False
    conn.voice_turn_id = None
    conn.voice_turn_ingress_state = "idle"
    conn.voice_turn_done.set()


async def handle_voice_turn_event(conn, event: VoiceInputEvent) -> None:
    if event.event_type == VoiceInputEventType.START:
        if not _matches_current_turn(conn, event.turn_id):
            return
        if conn.asr.interface_type == InterfaceType.STREAM:
            await _reset_stream_provider(conn)
            if not _matches_current_turn(conn, event.turn_id):
                return
        vad_resume_task = getattr(conn, "vad_resume_task", None)
        if vad_resume_task and not vad_resume_task.done():
            vad_resume_task.cancel()
        conn.just_woken_up = False
        conn.voice_turn_consumer_id = event.turn_id
        conn.voice_turn_frames = []
        conn.reset_audio_states()
        return

    if (
        event.turn_id != conn.voice_turn_consumer_id
        or not _matches_current_turn(conn, event.turn_id)
    ):
        return

    if event.event_type == VoiceInputEventType.AUDIO:
        if event.audio is None or conn.voice_turn_ingress_state in {"aborting", "failed", "terminal"}:
            return
        try:
            await handleAudioMessage(conn, event.audio, turn_id=event.turn_id)
            if (
                not _matches_current_turn(conn, event.turn_id)
                or conn.voice_turn_ingress_state not in {"open", "ending"}
            ):
                if conn.asr.interface_type == InterfaceType.STREAM:
                    await _reset_stream_provider(conn)
                return
            if conn.asr.interface_type != InterfaceType.STREAM:
                conn.voice_turn_frames.append(event.audio)
        except Exception as error:
            conn.logger.bind(tag=TAG).error(
                f"处理语音轮次音频失败: {type(error).__name__}"
            )
            conn.reset_audio_states()
            await send_voice_turn_ack(conn, event.turn_id, "error")
            complete_voice_turn(conn, event.turn_id)
        return

    if event.event_type == VoiceInputEventType.END:
        if conn.voice_turn_ingress_state == "aborting":
            return
        context = TurnContext(
            turn_id=event.turn_id,
            state="finalizing",
            frames=tuple(conn.voice_turn_frames),
        )
        conn.voice_turn_ingress_state = "finalizing"
        if conn.asr.interface_type == InterfaceType.STREAM:
            conn.client_voice_stop = True
            try:
                stopped = await asyncio.wait_for(
                    conn.asr._send_stop_request(), timeout=2
                )
            except (asyncio.TimeoutError, Exception):
                stopped = False
            if (
                not stopped
                or not _matches_current_turn(conn, event.turn_id)
                or conn.voice_turn_ingress_state != "finalizing"
            ):
                if conn.voice_turn_ingress_state == "terminal":
                    return
                if _matches_current_turn(conn, event.turn_id):
                    await _reset_stream_provider(conn)
                    await send_voice_turn_ack(conn, event.turn_id, "error")
                    complete_voice_turn(conn, event.turn_id)
                return
            await send_voice_turn_ack(conn, event.turn_id, "stopped")
            if is_voice_turn_active(conn, event.turn_id):
                conn.voice_turn_finalize_task = asyncio.create_task(
                    _stream_turn_timeout(conn, event.turn_id)
                )
        else:
            conn.reset_audio_states()
            await send_voice_turn_ack(conn, event.turn_id, "stopped")
            if not context.frames:
                await send_voice_turn_ack(conn, event.turn_id, "no_speech")
                complete_voice_turn(conn, event.turn_id)
                return
            conn.voice_turn_finalize_task = asyncio.create_task(
                conn.asr.handle_voice_stop(
                    conn, list(context.frames), turn_id=event.turn_id
                )
            )
        return

    if event.event_type == VoiceInputEventType.ABORT:
        conn.reset_audio_states()
        if conn.asr.interface_type == InterfaceType.STREAM:
            await _reset_stream_provider(conn)
        if event.terminal_state == "cancelled":
            await handleAbortMessage(conn)
            await send_voice_turn_ack(conn, event.turn_id, "cancelled")
        elif event.terminal_state == "error":
            await send_voice_turn_ack(conn, event.turn_id, "error")
        complete_voice_turn(conn, event.turn_id)


async def run_voice_turn_event(conn, event: VoiceInputEvent) -> None:
    task = asyncio.current_task()
    conn.voice_turn_event_task = task
    try:
        await handle_voice_turn_event(conn, event)
    finally:
        if conn.voice_turn_event_task is task:
            conn.voice_turn_event_task = None


async def _fail_voice_turn(conn, turn_id: str) -> None:
    if not _matches_current_turn(conn, turn_id):
        return
    await _put_control_event(
        conn,
        VoiceInputEvent(
            VoiceInputEventType.ABORT,
            turn_id,
            terminal_state="error",
        ),
    )


async def _put_control_event(conn, event: VoiceInputEvent) -> None:
    try:
        conn.voice_turn_queue.put_nowait(event)
    except queue.Full:
        await _hard_fail_voice_turn(conn, event.turn_id)


async def fail_voice_turn_event(conn, turn_id: str) -> None:
    if not _matches_current_turn(conn, turn_id):
        return
    if conn.stop_event.is_set() or conn.voice_turn_ingress_state in {
        "failed", "aborting", "terminal"
    }:
        return
    await send_voice_turn_ack(conn, turn_id, "error")
    complete_voice_turn(conn, turn_id)


def _matches_current_turn(conn, turn_id: str) -> bool:
    return (
        conn.voice_turn_v2_enabled
        and conn.voice_turn_id == turn_id
    )


def _matches_open_turn(conn, turn_id: str) -> bool:
    return (
        _matches_current_turn(conn, turn_id)
        and conn.voice_turn_ingress_state == "open"
    )


def is_voice_turn_active(conn, turn_id: str) -> bool:
    return (
        _matches_current_turn(conn, turn_id)
        and conn.voice_turn_ingress_state == "finalizing"
        and not conn.stop_event.is_set()
    )


def complete_voice_turn(conn, turn_id: str) -> bool:
    if not _matches_current_turn(conn, turn_id):
        return False
    _cancel_voice_turn_finalizer(conn)
    conn.voice_turn_frames = []
    conn.voice_turn_consumer_id = None
    conn.voice_turn_received_frames = 0
    conn.voice_turn_received_bytes = 0
    conn.voice_turn_ingress_state = "terminal"
    conn.voice_turn_done.set()
    return True


def _cancel_voice_turn_finalizer(conn) -> None:
    task = getattr(conn, "voice_turn_finalize_task", None)
    current = asyncio.current_task()
    if task and task is not current and not task.done():
        task.cancel()
    if task is not current:
        conn.voice_turn_finalize_task = None


def _cancel_voice_turn_event(conn) -> None:
    task = getattr(conn, "voice_turn_event_task", None)
    if task and task is not asyncio.current_task() and not task.done():
        task.cancel()
    future = getattr(conn, "voice_turn_event_future", None)
    if task is not asyncio.current_task() and future and not future.done():
        future.cancel()


async def _cancel_and_wait_voice_turn_event(conn) -> None:
    task = getattr(conn, "voice_turn_event_task", None)
    current = asyncio.current_task()
    _cancel_voice_turn_event(conn)
    if task and task is not current and not task.done():
        try:
            await asyncio.wait_for(task, timeout=2)
        except (asyncio.CancelledError, asyncio.TimeoutError):
            pass


async def _hard_fail_voice_turn(conn, turn_id: str) -> None:
    if not _matches_current_turn(conn, turn_id):
        return
    conn.voice_turn_ingress_state = "failed"
    await _cancel_and_wait_voice_turn_event(conn)
    _cancel_voice_turn_finalizer(conn)
    if conn.asr and conn.asr.interface_type == InterfaceType.STREAM:
        await _reset_stream_provider(conn)
    while True:
        try:
            conn.voice_turn_queue.get_nowait()
        except queue.Empty:
            break
    conn.reset_audio_states()
    conn.voice_turn_consumer_id = None
    await send_voice_turn_ack(conn, turn_id, "error")
    complete_voice_turn(conn, turn_id)


async def _stream_turn_timeout(conn, turn_id: str) -> None:
    try:
        await asyncio.sleep(30)
        if is_voice_turn_active(conn, turn_id):
            if conn.asr.interface_type == InterfaceType.STREAM:
                await _reset_stream_provider(conn)
            await send_voice_turn_ack(conn, turn_id, "error")
            complete_voice_turn(conn, turn_id)
    except asyncio.CancelledError:
        return


async def _reset_stream_provider(conn) -> None:
    provider = conn.asr
    task = getattr(provider, "forward_task", None)
    current = asyncio.current_task()
    if task and task is not current and not task.done():
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
        except Exception:
            pass
    old_ws = getattr(provider, "asr_ws", None)
    provider.asr_ws = None
    provider.forward_task = None
    if hasattr(provider, "is_processing"):
        provider.is_processing = False
    if hasattr(provider, "server_ready"):
        provider.server_ready = False
    if hasattr(provider, "_is_stopping"):
        provider._is_stopping = False
    if hasattr(provider, "text"):
        provider.text = ""
    if hasattr(provider, "task_id"):
        provider.task_id = None
    if old_ws:
        try:
            await asyncio.wait_for(old_ws.close(), timeout=2)
        except Exception:
            pass


def _ensure_voice_turn_consumer(conn) -> bool:
    if conn.asr is None or not hasattr(conn.asr, "voice_turn_priority_thread"):
        return False
    thread = getattr(conn, "voice_turn_priority_thread", None)
    if thread and thread.is_alive():
        return True
    thread = threading.Thread(
        target=conn.asr.voice_turn_priority_thread,
        args=(conn,),
        daemon=True,
    )
    conn.voice_turn_priority_thread = thread
    thread.start()
    return True


async def shutdown_voice_turn(conn) -> None:
    await _cancel_and_wait_voice_turn_event(conn)
    task = getattr(conn, "voice_turn_finalize_task", None)
    if task and not task.done():
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
    conn.voice_turn_finalize_task = None
    if conn.voice_turn_id:
        complete_voice_turn(conn, conn.voice_turn_id)
    conn.voice_turn_v2_enabled = False
