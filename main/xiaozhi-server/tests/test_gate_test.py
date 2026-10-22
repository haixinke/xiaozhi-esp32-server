# Sandcastle 门禁端到端测试文件，验证 Python 3.12 compileall 门禁可触发
from core.utils.gate_test import gate_check_python


def test_gate_check_python_returns_ok():
    assert gate_check_python() == "ok"
