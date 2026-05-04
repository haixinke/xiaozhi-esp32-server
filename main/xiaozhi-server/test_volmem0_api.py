#!/usr/bin/env python3
"""测试火山引擎 Mem0 API 路径"""

import requests
import json

# 从配置中读取（这里使用示例，实际需要从 data/.config.yaml 读取）
API_KEY = "your-api-key"  # 需要替换
HOST = "your-host:8000"   # 需要替换

def test_api_path(base_path):
    """测试 API 路径是否可访问"""
    url = f"{HOST}/{base_path}"
    print(f"\n测试路径: {url}")

    try:
        response = requests.get(
            url,
            headers={"Authorization": f"Token {API_KEY}"},
            timeout=5
        )
        print(f"  状态码: {response.status_code}")
        if response.status_code != 404:
            print(f"  响应: {response.text[:200]}")
            return True
        return False
    except Exception as e:
        print(f"  错误: {str(e)}")
        return False

if __name__ == "__main__":
    print("="*60)
    print("火山引擎 Mem0 API 路径测试")
    print("="*60)

    # 测试不同的 API 路径
    test_paths = [
        "v1/memories/",
        "v2/memories/",
        "api/v1/memories/",
        "api/v2/memories/",
        "memories/",
    ]

    print("\n【测试搜索路径】")
    for path in test_paths:
        test_api_path(f"{path}/search/")

    print("\n【测试添加路径】")
    for path in test_paths:
        test_api_path(path)

    print("\n【测试任务状态路径】")
    job_paths = [
        "v1/job/test123/",
        "v2/job/test123/",
        "api/v1/job/test123/",
        "job/test123/",
    ]
    for path in job_paths:
        test_api_path(path)

    print("\n" + "="*60)
    print("测试完成")
    print("="*60)
