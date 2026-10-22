# 门禁测试：xiaozhi-server 加一个空工具函数

**Status:** open

在 `main/xiaozhi-server/core/utils/` 下新增 `gate_test.py`，内容：

```python
"""Sandcastle 门禁端到端测试占位模块。"""


def gate_check_python() -> str:
    """返回固定字符串，验证 Python 3.12 compileall 门禁可触发。"""
    return "ok"
```

要求：
- 在 `main/xiaozhi-server/tests/` 下加 `test_gate_test.py`（pytest 风格，但只断言返回值）
- 文件头注释说明这是 sandcastle 门禁测试文件
- 不要改任何其它文件
