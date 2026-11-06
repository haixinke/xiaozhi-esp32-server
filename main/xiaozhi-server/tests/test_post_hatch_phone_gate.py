"""验证破壳后手机号门槛经配置接口传递到聊天初始化边界。"""

import asyncio

import httpx
import pytest

from config.config_loader import get_private_config_from_api
from config.manage_api_client import (
    DeviceBindException,
    DeviceNotFoundException,
    ManageApiClient,
    PhoneBindingRequiredException,
)


def load_private_config(monkeypatch, agent_response, requests=None):
    """仅替换 HTTP 传输，保留真实 API 解析和并发配置编排。"""

    async def scenario():
        def respond(request):
            if requests is not None:
                requests.append(request.url.path)
            if request.url.path == "/config/agent-models":
                return httpx.Response(200, json=agent_response)
            return httpx.Response(200, json={"code": 0, "data": []})

        async with httpx.AsyncClient(
            base_url="https://manager-api.invalid",
            transport=httpx.MockTransport(respond),
        ) as client:
            async def ensure_client(cls):
                return client

            monkeypatch.setattr(
                ManageApiClient, "_ensure_async_client", classmethod(ensure_client)
            )
            monkeypatch.setattr(
                ManageApiClient, "_instance", object.__new__(ManageApiClient)
            )
            monkeypatch.setattr(ManageApiClient, "max_retries", 3, raising=False)
            return await get_private_config_from_api(
                {"selected_module": {}}, "test-pet-device", "test-client"
            )

    return asyncio.run(scenario())


def test_phone_required_response_cannot_become_usable_empty_config(monkeypatch):
    """未绑定账号必须被阻断，不能因配置降级而开始聊天。"""
    with pytest.raises(PhoneBindingRequiredException, match="请先绑定手机号"):
        load_private_config(
            monkeypatch, {"code": 10228, "msg": "请先绑定手机号", "data": None}
        )


def test_phone_required_is_not_retried_or_exposed_as_upstream_debug(monkeypatch):
    """用户动作才能解除的门槛不做网络重试，也不回显上游调试信息。"""
    requests = []
    with pytest.raises(PhoneBindingRequiredException) as error:
        load_private_config(
            monkeypatch, {"code": 10228, "msg": "upstream debug details"}, requests
        )
    assert str(error.value) == "请先绑定手机号"
    assert requests.count("/config/agent-models") == 1


def test_bound_account_config_remains_available(monkeypatch):
    """绑定后由服务端正常返回配置，无需重领宠物或重建设备。"""
    assert load_private_config(
        monkeypatch, {"code": 0, "data": {"prompt": "test companion"}}
    ) == {"prompt": "test companion"}


@pytest.mark.parametrize(
    ("code", "exception"),
    [(10041, DeviceNotFoundException), (10042, DeviceBindException)],
)
def test_existing_device_rejections_still_block_config(monkeypatch, code, exception):
    """手机号门槛不改变原有设备缺失和待绑定拒绝路径。"""
    with pytest.raises(exception):
        load_private_config(monkeypatch, {"code": code, "msg": "test rejection"})
