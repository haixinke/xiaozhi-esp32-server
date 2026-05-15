"""
宠物相关的工具函数和常量
"""

from datetime import datetime
from typing import Dict, Optional, Union

# 星座英文编码到中文的映射
ZODIAC_EN_TO_ZH: Dict[str, str] = {
    "aries": "白羊座",
    "taurus": "金牛座",
    "gemini": "双子座",
    "cancer": "巨蟹座",
    "leo": "狮子座",
    "virgo": "处女座",
    "libra": "天秤座",
    "scorpio": "天蝎座",
    "sagittarius": "射手座",
    "capricorn": "摩羯座",
    "aquarius": "水瓶座",
    "pisces": "双鱼座",
}


def get_zodiac_chinese(zodiac_en: str) -> str:
    """
    将英文星座编码转换为中文名称

    Args:
        zodiac_en: 英文星座编码（如 "aries", "taurus"）

    Returns:
        中文名称（如 "白羊座", "金牛座"），如果未找到则返回原值
    """
    return ZODIAC_EN_TO_ZH.get(zodiac_en.lower(), zodiac_en)


def format_birth_date(birth_date: Union[str, datetime, None]) -> Optional[str]:
    """
    格式化出生日期为中文格式

    Args:
        birth_date: 出生日期，可以是字符串或 datetime 对象

    Returns:
        格式化后的日期字符串（如 "2024年3月15日"），失败返回 None
    """
    if not birth_date:
        return None

    try:
        # birthDate 可能是 ISO 字符串或 datetime 对象
        if isinstance(birth_date, str):
            birth_date = datetime.fromisoformat(birth_date.replace("Z", "+00:00"))

        # 格式化为 "2024年3月15日"
        return birth_date.strftime("%Y年%-m月%-d日").replace("-", "")
    except Exception as e:
        # 记录错误日志，便于调试
        from config.logger import setup_logging
        logger = setup_logging()
        logger.bind(tag="pet_utils").warning(
            f"解析出生日期失败: {e}, birth_date_type={type(birth_date).__name__}, "
            f"birth_date_value={repr(birth_date)[:100]}"
        )
        return None


def format_pet_birth_info(pet_info: dict) -> str:
    """
    格式化宠物出生信息为提示词片段

    Args:
        pet_info: 宠物信息字典，包含 birthDate 和 zodiac 字段

    Returns:
        格式化后的字符串，如 "出生于2024年3月15日，星座是白羊座"

    Examples:
        >>> pet_info = {
        ...     "birthDate": "2024-03-15T14:30:00Z",
        ...     "zodiac": "aries"
        ... }
        >>> format_pet_birth_info(pet_info)
        '出生于2024年3月15日，星座是白羊座'
    """
    parts = []

    # 添加出生日期（格式：出生于2024年3月15日）
    birth_date_str = format_birth_date(pet_info.get("birthDate"))
    if birth_date_str:
        parts.append(f"出生于{birth_date_str}")

    # 添加星座（中文）
    if pet_info.get("zodiac"):
        zodiac_en = pet_info["zodiac"]
        zodiac_zh = get_zodiac_chinese(zodiac_en)
        parts.append(f"星座是{zodiac_zh}")

    return "，".join(parts)
