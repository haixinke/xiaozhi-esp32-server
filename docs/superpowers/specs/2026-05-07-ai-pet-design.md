# AI Pet Domain Design

## Overview

Add a new "AI Pet" domain to manager-api for managing AI pets. Each pet is associated with one device and belongs to one user. When a pet device calls the birth API, the system automatically calculates BaZi (Eight Characters), WuXing (Five Elements), and Zodiac from the birth time, then uses LLM to derive the pet's MBTI personality.

## Data Model

### Table: `ai_pet`

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | VARCHAR(32) | PK | UUID primary key |
| `user_id` | BIGINT | NOT NULL, INDEX | Owner user, looked up from device table |
| `device_id` | VARCHAR(32) | NOT NULL, UNIQUE | Associated device, one device one pet |
| `nickname` | VARCHAR(50) | | Nickname, randomly assigned from default list |
| `birth_date` | DATETIME | NOT NULL | Birth date/time (hour-level precision) |
| `bazi` | JSON | | BaZi, e.g. `{"year":"丙午","month":"壬辰","day":"乙亥","hour":"丙戌"}` |
| `wuxing` | JSON | | WuXing with English keys, e.g. `{"metal":0,"wood":2,"water":3,"fire":5,"earth":4}` |
| `zodiac` | VARCHAR(20) | | Zodiac English code, e.g. `taurus` |
| `mbti` | VARCHAR(4) | | MBTI personality, e.g. `INTJ` |
| `creator` | BIGINT | | Creator |
| `create_date` | DATETIME | | Created at |
| `updater` | BIGINT | | Updater |
| `update_date` | DATETIME | | Updated at |

**Constraints:**
- `device_id` is UNIQUE (one device can only have one pet)
- `user_id` has a normal index for query performance

## API Endpoints

### POST /pet/birth

**Caller:** Device

**Request:**
| Parameter | Type | Required | Description |
|---|---|---|---|
| `deviceId` | String | Yes | Device ID |

**Flow:**
1. Validate deviceId exists and is bound to a user
2. Validate the device has no existing pet
3. Use current server time as birth time
4. Call `PetBirthCalculator` to compute BaZi, WuXing, Zodiac
5. Build prompt with BaZi + WuXing, call LLM to derive MBTI
6. Randomly assign nickname from default list
7. Insert into `ai_pet` table
8. Return complete pet info

**Degradation:** If LLM call fails, default MBTI to `INFP`

### GET /pet/detail/{deviceId}

**Caller:** User / Device

**Response:** Pet info for the given device. Returns error if not found.

### GET /pet/list

**Caller:** User (management console)

**Response:** List of all pets belonging to the current user.

### PUT /pet/update

**Caller:** User (management console)

**Request:**
| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | String | Yes | Pet ID |
| `nickname` | String | No | Nickname |

**Permission:** Users can only edit their own pets.

## BaZi / WuXing Calculation

Use [lunar-java](https://github.com/6tail/lunar-java) library (5k+ GitHub stars):

```xml
<dependency>
    <groupId>com.github.6tail</groupId>
    <artifactId>lunar</artifactId>
</dependency>
```

Encapsulated in `PetBirthCalculator` utility class. Input: `LocalDateTime`. Output:
- **BaZi:** Year pillar, month pillar, day pillar, hour pillar (heavenly stem + earthly branch)
- **WuXing:** Count occurrences of metal/wood/water/fire/earth in BaZi (range 0-5), stored with English keys
- **Zodiac:** Calculated from month and day, stored as English code (`aries`, `taurus`, `gemini`, `cancer`, `leo`, `virgo`, `libra`, `scorpio`, `sagittarius`, `capricorn`, `aquarius`, `pisces`)

## LLM-based MBTI Derivation

Reuses existing LLM infrastructure in `xiaozhi.modules.llm`. No new dependencies.

**Prompt:**
```
根据以下八字和五行信息，推算这个AI宠物的MBTI人格类型。

八字：年柱-丙午，月柱-壬辰，日柱-乙亥，时柱-丙戌
五行：金-0，木-2，水-3，火-5，土-4

请只回复四个字母的MBTI类型，不要其他内容。
```

**Parsing:** Extract a valid 4-letter MBTI code (16 types) from LLM response. Invalid values fall back to `INFP`.

## Default Nickname List

Configured in `application.yml` or database dictionary table. Initial list of 20-30 names, e.g.:

`小团子, 豆豆, 年糕, 糯米, 芒果, 布丁, ...`

Supports management console maintenance in the future.

## Module Structure

New module `xiaozhi.modules.pet` following existing package conventions:

```
xiaozhi/modules/pet/
├── controller/
│   └── PetController.java
├── service/
│   └── PetService.java
├── service/impl/
│   └── PetServiceImpl.java
├── dao/
│   └── PetDao.java
├── entity/
│   └── PetEntity.java
├── dto/
│   └── PetBirthDTO.java
├── vo/
│   └── PetVO.java
└── util/
    └── PetBirthCalculator.java
```

**Dependencies:** pet module depends on `device` module (user lookup) and `llm` module (LLM call). Only new library dependency is `lunar`.

## Design Decisions

1. **Synchronous birth flow:** Pet birth is a one-time event during device provisioning. Users expect some wait time. Synchronous is simpler and sufficient. No need for async mechanisms.
2. **English codes for storage:** WuXing keys and Zodiac values stored in English for consistency and internationalization readiness. Chinese labels can be added at the VO/display layer.
3. **device_id UNIQUE constraint:** Enforces one-pet-per-device at the database level.
4. **Pet independent from Agent:** AI Pet is a standalone domain, not an extension of Agent.
