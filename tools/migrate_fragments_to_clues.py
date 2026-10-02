"""把旧的 fragment_data.json 迁移成里程碑 2 的线索内容 clues.json。

为什么用脚本而不是手写：正文必须**逐字**保留（这是玩家看到的原始材料），
手抄 12 段长文本必然出错；同时把「旧整数编号 → 稳定 ID」的映射写在脚本里，
迁移过程本身就变成可审计的一行行数据。

用法：
    python tools/migrate_fragments_to_clues.py

输出：
    src/main/resources/dreamingfishcore/defaults/clues.json
（玩家可见内容随 jar 分发，因此这里**只写玩家能看的字段**：
 作者立场 / 证据包 / 隐藏证据关系 / 真伪 一律留在服主私密的 clue_secrets.json，本脚本不产出。）
"""

from __future__ import annotations

import json
from pathlib import Path

# 旧编号 → (稳定 ID 后缀, 来源, 观察跨度, 样本, 观察条件)
METADATA = {
    1: ("observation_ward_log", "阿拜多斯医院 · 观察区",
        "第 2 天深夜至第 3 天 0:12", "1 例（患者 7 号）", "隔离观察区内、医疗值守下复检"),
    2: ("reversal_agent_manual", "梦屿中央医院药剂科",
        "本批药品的当前观察窗口", "未标注（应急版说明书）", "医疗人员监护下使用，需先通过医学评估"),
    3: ("note_outside_quarantine", "隔离区外一名家属（未署名）",
        "第 3 天午后（单次留言）", "1 名被隔离者（未具名）", "手写字条，随药品送进隔离区"),
    4: ("recovered_voice", "阿拜多斯医院 · 随访记录",
        "第 5 天上午一次随访", "1 例（K 先生）", "疗程完成后次日随访口述"),
    5: ("base_floor_plan_draft", "逐光联合会筹备处（周岑代管）",
        "第 2 天黄昏（草案）", "未定选址", "手绘草图，未定稿"),
    6: ("preparatory_meeting_minutes", "人类逐光联合会筹备处",
        "第 3 天晚间一次会议", "六个参会方", "会议记录片段，未列决议"),
    7: ("rescue_handover_receipt", "逐光联合会物流组",
        "第 4 天傍晚一次交接", "1 批药品（清单 24 支 / 签收 19 支）", "夜间无人值守期间的交接记录"),
    8: ("outer_relay_signal", "外缘中继站",
        "第 2 天凌晨，03:12 信号中断", "1 段应急频段通话", "中继站本地设备留存"),
    9: ("tonglan_observatory_log", "通兰天文台",
        "第 4 天夜，一次链路恢复", "1 段公开频道节录", "公开频道节录，完整数据存档于天文台"),
    10: ("medical_review_opinion", "医学评审",
         "第 3 天下午，基于 7 号病例", "1 例（观察区 7 号病例）", "评审意见摘录，含与主治医生的分歧"),
    11: ("respawn_node_anomaly", "重生管理处",
         "第 2 天中午，节点 04 校准", "感染者模板重建（未标注例数）", "节点校准记录节录"),
    12: ("townsfolk_voice", "阿拜多斯 · 口述记录",
         "第 4 天清晨一次访谈", "1 名镇民", "街访口述，非正式记录"),
}

ID_PREFIX = "dreamingfishcore:clue/"


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    defaults = root / "src" / "main" / "resources" / "dreamingfishcore" / "defaults"
    source_path = defaults / "fragment_data.json"
    target_path = defaults / "clues.json"

    legacy = json.loads(source_path.read_text(encoding="utf-8"))
    if len(legacy) != len(METADATA):
        raise SystemExit(f"旧内容条数（{len(legacy)}）与映射表（{len(METADATA)}）不一致，先核对再迁移")

    migrated = []
    for entry in legacy:
        legacy_id = entry["id"]
        if legacy_id not in METADATA:
            raise SystemExit(f"旧编号 {legacy_id} 没有对应的新 ID 映射")
        slug, source, span, sample, conditions = METADATA[legacy_id]
        migrated.append({
            "id": ID_PREFIX + slug,
            "legacyId": legacy_id,
            "stageId": entry["stageId"],
            "chapterId": entry["chapterId"],
            "title": entry["title"],
            "authorName": entry["authorName"],
            "time": entry["time"],
            "content": entry["content"],
            "source": source,
            "observationSpan": span,
            "sample": sample,
            "conditions": conditions,
        })

    target_path.write_text(
        json.dumps(migrated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已迁移 {len(migrated)} 条线索 → {target_path}")
    for item in migrated:
        print(f"  {item['legacyId']:>2} → {item['id']}")


if __name__ == "__main__":
    main()
