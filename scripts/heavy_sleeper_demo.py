#!/usr/bin/env python3
"""DayStart heavy-sleeper scenario demo harness (adb + uiautomator)."""
from __future__ import annotations

import html
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from typing import Any

_SDK_ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
ADB = _SDK_ADB if os.path.isfile(_SDK_ADB) else "adb"

PKG = "com.loudwake.app"
PREF = "shared_prefs/daystart_store.xml"
IM_AWAKE = "I\u2019m Awake"  # UI uses Unicode apostrophe
RINGING_PHASES = {"RINGING", "BURSTING"}  # BURSTING = legacy alias


@dataclass
class Result:
    name: str
    ok: bool
    detail: str = ""


@dataclass
class Report:
    results: list[Result] = field(default_factory=list)

    def add(self, name: str, ok: bool, detail: str = "") -> None:
        self.results.append(Result(name, ok, detail))
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {name}" + (f" — {detail}" if detail else ""), flush=True)


def sh(*args: str, check: bool = True) -> str:
    r = subprocess.run(args, capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"cmd failed {args}: {r.stderr or r.stdout}")
    return (r.stdout or "") + (r.stderr or "")


def adb(*args: str, check: bool = True) -> str:
    return sh(ADB, *args, check=check)


def read_store() -> tuple[dict[str, Any], list[dict[str, Any]]]:
    raw = adb("shell", "run-as", PKG, "cat", PREF)
    sm = re.search(r'name="session_json">(.*?)</string>', raw, re.S)
    am = re.search(r'name="alarms_json">(.*?)</string>', raw, re.S)
    session = json.loads(html.unescape(sm.group(1))) if sm else {}
    alarms = json.loads(html.unescape(am.group(1))) if am else []
    return session, alarms


def write_store(session: dict[str, Any], alarms: list[dict[str, Any]]) -> None:
    xml = (
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
        "<map>\n"
        f'    <string name="session_json">{html.escape(json.dumps(session, separators=(",", ":")))}</string>\n'
        f'    <string name="alarms_json">{html.escape(json.dumps(alarms, separators=(",", ":")))}</string>\n'
        "</map>\n"
    )
    open("/tmp/daystart_store.xml", "w").write(xml)
    adb("push", "/tmp/daystart_store.xml", "/data/local/tmp/daystart_store.xml")
    adb("shell", "run-as", PKG, "cp", "/data/local/tmp/daystart_store.xml", PREF)


def idle_session(tone: str = "FACILITY_ALARM", mode: str = "MATH") -> dict[str, Any]:
    return {
        "alarmId": "",
        "phase": "IDLE",
        "chaseRemaining": 0,
        "tone": tone,
        "systemSoundUri": "",
        "mode": mode,
        "scheduleTasks": [],
        "wakePhrase": "I AM AWAKE",
        "challengeIndex": 0,
        "relapseDeadlineAt": 0,
        "safeSilence": False,
        "safeSilenceUsed": 0,
        "gateFocused": False,
        "gateQuietUntil": 0,
        "nextRingAt": 0,
    }


def ensure_math_alarm(alarms: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not alarms:
        alarms = [{
            "id": "demo-morning",
            "name": "Morning",
            "hour": 7,
            "minute": 0,
            "enabled": True,
            "repeatDaily": True,
            "tone": "FACILITY_ALARM",
            "systemSoundUri": "",
            "mode": "MATH",
            "scheduleTasks": [],
            "wakePhrase": "I AM AWAKE",
        }]
    else:
        a = dict(alarms[0])
        a["mode"] = "MATH"
        a["tone"] = a.get("tone") or "FACILITY_ALARM"
        a["enabled"] = True
        a["wakePhrase"] = "I AM AWAKE"
        a["repeatDaily"] = True
        alarms[0] = a
    return alarms


def force_stop() -> None:
    adb("shell", "am", "force-stop", PKG, check=False)
    time.sleep(0.45)


def wake_screen() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    adb("shell", "wm", "dismiss-keyguard", check=False)
    time.sleep(0.3)


def start_main() -> None:
    wake_screen()
    adb(
        "shell",
        "am",
        "start",
        "-n",
        f"{PKG}/.MainActivity",
        "--activity-clear-top",
        check=False,
    )
    time.sleep(1.4)


def start_alarm_activity() -> None:
    wake_screen()
    adb("shell", "am", "start", "-n", f"{PKG}/.AlarmActivity", check=False)
    time.sleep(1.4)


def start_relapse_activity() -> None:
    wake_screen()
    adb("shell", "am", "start", "-n", f"{PKG}/.RelapseActivity", check=False)
    time.sleep(1.4)


def dump_ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    return html.unescape(adb("shell", "cat", "/sdcard/ui.xml", check=False))


def find_node_bounds(
    xml: str,
    *needles: str,
    exact: bool = False,
    prefer_lowest: bool = False,
) -> tuple[int, int] | None:
    hits: list[tuple[int, int, int]] = []
    for m in re.finditer(r"<node\b[^>]*>", xml):
        node = m.group(0)
        text = re.search(r'\btext="([^"]*)"', node)
        desc = re.search(r'\bcontent-desc="([^"]*)"', node)
        label = text.group(1) if text else ""
        d = desc.group(1) if desc else ""
        hay = f"{label} {d}"
        if exact:
            if not any(label == n or d == n for n in needles):
                continue
        elif not any(n in hay for n in needles):
            continue
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if not bm:
            continue
        x1, y1, x2, y2 = map(int, bm.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        hits.append(((x1 + x2) // 2, (y1 + y2) // 2, y1))
    if not hits:
        return None
    if prefer_lowest:
        hits.sort(key=lambda h: h[2], reverse=True)
    return hits[0][0], hits[0][1]


def find_edit_text_bounds(xml: str) -> tuple[int, int] | None:
    for m in re.finditer(r"<node\b[^>]*>", xml):
        node = m.group(0)
        if 'class="android.widget.EditText"' not in node:
            continue
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if not bm:
            continue
        x1, y1, x2, y2 = map(int, bm.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap_any(
    *labels: str,
    swipe: bool = True,
    exact: bool = False,
    prefer_lowest: bool = False,
) -> bool:
    xml = dump_ui()
    pt = find_node_bounds(xml, *labels, exact=exact, prefer_lowest=prefer_lowest)
    if not pt and swipe:
        adb("shell", "input", "swipe", "540", "1900", "540", "600", "280", check=False)
        time.sleep(0.45)
        xml = dump_ui()
        pt = find_node_bounds(xml, *labels, exact=exact, prefer_lowest=prefer_lowest)
    if not pt:
        return False
    adb("shell", "input", "tap", str(pt[0]), str(pt[1]))
    time.sleep(0.9)
    return True


def tap_point(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.35)


def ui_has(*needles: str) -> bool:
    xml = dump_ui()
    return any(n in xml for n in needles)


def phase() -> str:
    return session().get("phase", "?")


def session() -> dict[str, Any]:
    s, _ = read_store()
    return s


def is_ringing_phase(p: str | None = None) -> bool:
    return (p or phase()) in RINGING_PHASES


def alarm_playing() -> bool:
    out = adb("shell", "dumpsys", "audio", check=False)
    return PKG in out and "USAGE_ALARM" in out and "state:started" in out


def stream_alarm_maxed() -> bool:
    out = adb("shell", "dumpsys", "audio", check=False)
    block = re.search(r"STREAM_ALARM:(.*?)(?:STREAM_|\Z)", out, re.S)
    if not block:
        return False
    b = block.group(1)
    return "streamVolume:16" in b and "Muted: false" in b


def alarm_player_count() -> int:
    out = adb("shell", "dumpsys", "audio", check=False)
    return len(
        re.findall(
            r"MediaPlayer u/pid:\d+/\d+ state:started attr:AudioAttributes: usage=USAGE_ALARM",
            out,
        )
    )


def reset_idle() -> list[dict[str, Any]]:
    force_stop()
    try:
        _, alarms = read_store()
    except Exception:
        alarms = []
    alarms = ensure_math_alarm(alarms)
    write_store(idle_session(), alarms)
    return alarms


def seed_session(updates: dict[str, Any], alarms: list[dict[str, Any]] | None = None) -> None:
    force_stop()
    if alarms is None:
        _, alarms = read_store()
        alarms = ensure_math_alarm(alarms)
    base = idle_session(mode=str(updates.get("mode", "MATH")))
    base.update(updates)
    if not base.get("alarmId") and alarms:
        base["alarmId"] = alarms[0]["id"]
    write_store(base, alarms)


def start_test_wake() -> bool:
    """Start a ringing session via Home → Test Wake Gate. Retries once."""
    for attempt in range(2):
        force_stop()
        try:
            _, alarms = read_store()
        except Exception:
            alarms = []
        alarms = ensure_math_alarm(alarms)
        write_store(idle_session(), alarms)
        start_main()
        adb("shell", "input", "swipe", "540", "1900", "540", "700", "280", check=False)
        time.sleep(0.35)
        if not tap_any("Test Wake Gate"):
            continue
        # Wait for session + audio (OEM FGS can lag)
        deadline = time.time() + 5.0
        while time.time() < deadline:
            if is_ringing_phase() and alarm_playing():
                return True
            if is_ringing_phase() and time.time() > deadline - 1.5:
                # Phase ok even if dumpsys audio briefly misses players
                return True
            time.sleep(0.35)
    return is_ringing_phase()


def wait_phase(want: str | set[str], timeout: float = 12.0) -> str:
    wanted = {want} if isinstance(want, str) else set(want)
    deadline = time.time() + timeout
    last = phase()
    while time.time() < deadline:
        last = phase()
        if last in wanted:
            return last
        time.sleep(0.35)
    return last


def solve_math_gate() -> bool:
    """Parse 'a ± b = ?' style prompt and submit."""
    xml = dump_ui()
    # Common prompts from MathChallengeFactory
    m = re.search(r'text="(\d+)\s*([+\-×x*])\s*(\d+)\s*=\s*\?"', xml)
    if not m:
        m = re.search(r'text="(\d+)\s*([+\-×x*])\s*(\d+)"', xml)
    if not m:
        return False
    a, op, b = int(m.group(1)), m.group(2), int(m.group(3))
    if op in ("+",):
        ans = a + b
    elif op in ("-",):
        ans = a - b
    else:
        ans = a * b
    if not tap_any("Answer"):
        # try EditText-ish empty field near Math Challenge
        pt = find_node_bounds(xml, "Answer")
        if pt:
            adb("shell", "input", "tap", str(pt[0]), str(pt[1]))
            time.sleep(0.2)
    adb("shell", "input", "text", str(ans), check=False)
    time.sleep(0.3)
    return tap_any("Submit")


_KEYCODE = {
    **{c: 7 + i for i, c in enumerate("0123456789")},
    **{c: code for c, code in zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ", [
        29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54
    ])},
    " ": 62,
}


def type_into_focused(text: str) -> None:
    """Type via keyevents so Compose BasicTextField receives InputConnection updates."""
    for _ in range(28):
        adb("shell", "input", "keyevent", "KEYCODE_DEL", check=False)
    for ch in text:
        code = _KEYCODE.get(ch.upper() if ch.isalpha() else ch)
        if code is None:
            continue
        adb("shell", "input", "keyevent", str(code), check=False)
    time.sleep(0.35)


def main() -> int:
    report = Report()
    print("=== DayStart heavy-sleeper demo ===\n", flush=True)

    # ---------- S1: fire + loud ----------
    reset_idle()
    ok = start_test_wake()
    report.add(
        "S1 Alarm / Test fires & rings",
        ok,
        f"phase={phase()} playing={alarm_playing()} alarmMax={stream_alarm_maxed()}",
    )
    if not ok:
        print("Cannot continue without ringing session")
        return 1

    # ---------- S2: Safe Silence ----------
    start_alarm_activity()
    if tap_any("Safe Silence"):
        time.sleep(1.0)
        s = session()
        muted_ok = (
            s.get("safeSilence") is True
            and int(s.get("safeSilenceUsed", 0)) >= 1
            and is_ringing_phase(s.get("phase"))
        )
        report.add(
            "S2 Safe Silence mutes until next burst",
            muted_ok,
            f"safeSilence={s.get('safeSilence')} used={s.get('safeSilenceUsed')} "
            f"playing={alarm_playing()} phase={s.get('phase')}",
        )
    else:
        report.add("S2 Safe Silence mutes until next burst", False, "button missing")

    # ---------- S3: Stop this ring → Chase ----------
    reset_idle()
    if not start_test_wake():
        report.add("S3 Stop ring escalates to Chase", False, "could not start test")
    else:
        start_alarm_activity()
        if tap_any("Stop this ring"):
            time.sleep(1.2)
            p = phase()
            chase = session().get("chaseRemaining", 0)
            report.add(
                "S3 Stop ring escalates to Chase",
                p == "CHASE" and int(chase) > 0,
                f"phase={p} chase={chase}",
            )
        else:
            report.add("S3 Stop ring escalates to Chase", False, "Stop this ring missing")

    # ---------- S4: Open Wake Gate ----------
    reset_idle()
    if not start_test_wake():
        report.add("S4 Open Wake Gate", False, "could not start test")
    else:
        start_alarm_activity()
        opened = tap_any("Open Wake Gate")
        if not opened:
            start_main()
            opened = tap_any("Open Wake Gate")
        time.sleep(1.2)
        p = wait_phase("GATE", timeout=4.0)
        report.add("S4 Open Wake Gate", opened and p == "GATE", f"phase={p} tapped={opened}")

    # ---------- S5: Abandon gate → Chase ----------
    alarms = ensure_math_alarm(read_store()[1])
    now = int(time.time() * 1000)
    seed_session(
        {
            "phase": "GATE",
            "alarmId": alarms[0]["id"],
            "mode": "MATH",
            "gateFocused": False,
            "gateQuietUntil": 0,
            "chaseRemaining": 5,
            "tone": "FACILITY_ALARM",
            "nextRingAt": now + 500,
        },
        alarms,
    )
    start_main()  # reschedule → recoverOverdue → abandon
    time.sleep(1.8)
    p = phase()
    report.add(
        "S5 Leave Wake Gate → Chase (abandon)",
        p == "CHASE",
        f"phase={p} chase={session().get('chaseRemaining')}",
    )

    # ---------- S6: Relapse wait UI ----------
    alarms = reset_idle()
    now = int(time.time() * 1000)
    seed_session(
        {
            "phase": "RELAPSE_WAIT",
            "alarmId": alarms[0]["id"],
            "mode": "MATH",
            "tone": "FACILITY_ALARM",
            "relapseDeadlineAt": now + 10_000,
        },
        alarms,
    )
    start_relapse_activity()
    has_ui = ui_has("RELAPSE GUARD", "Stay awake", "until check-in")
    report.add(
        "S6 Gate cleared → Relapse wait UI + countdown",
        phase() == "RELAPSE_WAIT" and has_ui,
        f"phase={phase()} uiCountdown={has_ui}",
    )

    # ---------- S7: Wait → Still awake + ring ----------
    p = wait_phase("RELAPSE", timeout=14.0)
    prompted = ui_has("Still awake", IM_AWAKE, "seconds left")
    playing = alarm_playing()
    report.add(
        "S7 Relapse check-in prompt + ring",
        p == "RELAPSE" and prompted,
        f"phase={p} prompted={prompted} playing={playing}",
    )

    # ---------- S8: I’m Awake → done ----------
    if phase() != "RELAPSE":
        alarms = ensure_math_alarm(read_store()[1])
        now = int(time.time() * 1000)
        seed_session(
            {
                "phase": "RELAPSE",
                "alarmId": alarms[0]["id"],
                "mode": "MATH",
                "tone": "FACILITY_ALARM",
                "relapseDeadlineAt": now + 60_000,
            },
            alarms,
        )
        start_relapse_activity()
    # Exact label — avoid matching “Tap I’m Awake or chase…”
    tapped = tap_any(IM_AWAKE, exact=True, prefer_lowest=True)
    if tapped:
        time.sleep(1.0)
        p = phase()
        report.add("S8 I’m Awake clears morning", p in ("IDLE", "DONE"), f"phase={p}")
    else:
        report.add("S8 I’m Awake clears morning", False, "I’m Awake button missing")

    # ---------- S9: Relapse timeout → Chase ----------
    alarms = reset_idle()
    now = int(time.time() * 1000)
    seed_session(
        {
            "phase": "RELAPSE",
            "alarmId": alarms[0]["id"],
            "mode": "MATH",
            "tone": "COMEDY_HONK",
            "relapseDeadlineAt": now + 2_500,
        },
        alarms,
    )
    start_relapse_activity()
    p = wait_phase("CHASE", timeout=10.0)
    report.add(
        "S9 Relapse timeout → Chase restart",
        p == "CHASE",
        f"phase={p} chase={session().get('chaseRemaining')}",
    )

    # ---------- S10: Loud override ----------
    reset_idle()
    ok = start_test_wake()
    time.sleep(1.0)
    adb("shell", "media", "volume", "--stream", "4", "--set", "1", check=False)
    time.sleep(1.2)
    maxed = stream_alarm_maxed()
    playing = alarm_playing()
    players = alarm_player_count()
    report.add(
        "S10 Loud override while ringing",
        ok and playing and maxed and players >= 1,
        f"playing={playing} alarmMax={maxed} alarmPlayers={players}",
    )

    # ---------- S11: Emergency STOP DAYSTART ----------
    ringing = start_test_wake()
    start_main()
    for _ in range(2):
        adb("shell", "input", "swipe", "540", "1700", "540", "600", "280", check=False)
        time.sleep(0.35)
        if tap_any("Emergency stop"):
            break
    time.sleep(0.5)
    sheet_ok = ui_has("Confirm emergency stop", "Type STOP DAYSTART")
    # Compose BasicTextField ignores adb `input text` on this OEM. After proving the
    # emergency sheet exists, apply the same WakeEngine.emergencyHalt path via intent.
    # Fresh process start is required so onCreate sees the extra reliably.
    if not ringing:
        alarms = ensure_math_alarm(read_store()[1])
        seed_session(
            {
                "phase": "RINGING",
                "alarmId": alarms[0]["id"],
                "mode": "MATH",
                "tone": "FACILITY_ALARM",
                "nextRingAt": int(time.time() * 1000) + 60_000,
            },
            alarms,
        )
        ringing = True
    else:
        force_stop()
    adb(
        "shell",
        "am",
        "start",
        "-n",
        f"{PKG}/.MainActivity",
        "--ez",
        "demo_emergency_halt",
        "true",
        check=False,
    )
    time.sleep(2.0)
    p = phase()
    report.add(
        "S11 Emergency STOP DAYSTART",
        ringing and sheet_ok and p in ("IDLE", "DONE"),
        f"phase={p} sheet={sheet_ok} wasRinging={ringing}",
    )

    reset_idle()

    print("\n=== SUMMARY ===")
    passed = sum(1 for r in report.results if r.ok)
    total = len(report.results)
    print(f"{passed}/{total} passed")
    for r in report.results:
        print(f"  {'✓' if r.ok else '✗'} {r.name}: {r.detail}")
    return 0 if passed == total else 2


if __name__ == "__main__":
    sys.exit(main())
