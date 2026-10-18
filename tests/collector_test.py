#!/usr/bin/env python3
"""Run with Python 3 and pwsh. Uses a fake ADB process; never a real phone."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PWSH = os.environ.get('SWITCHPHONE_PWSH', 'pwsh')
SCRIPT = ROOT / 'scripts/windows/Collect-Bluetooth.ps1'
SENTINEL = 'PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY'

FAKE = r'''#!/usr/bin/env python3
import json, os, sys, time
args = sys.argv[1:]
case = os.environ['SWITCHPHONE_ADB_CASE']
with open(os.environ['SWITCHPHONE_ADB_ARGS'], 'a') as out:
    out.write(json.dumps(args)+'\n')
print('PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY AA:BB:CC:DD:EE:FF key=123456', file=sys.stderr)
if args == ['-d', 'get-state']:
    if case == 'unauthorized': sys.exit(1)
    print('device')
elif args == ['-d', 'shell', 'pidof', 'com.android.bluetooth']:
    print('1234;BAD' if case == 'bad_pid' else '1234')
elif args == ['-d', 'shell', 'date', '+%s.%N']:
    print('bad-clock' if case == 'bad_clock' else '1792000000.123456789')
elif args == ['-d', 'shell', 'date', '+%Y-%m-%d_%H:%M:%S']:
    print('bad-clock' if case == 'bad_clock' else '2026-10-14_16:30:00')
elif args == ['-d','shell','dumpsys','bluetooth_manager']:
    if case == 'service_denied':
        print('Permission Denial: PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY')
    else:
        print('Bluetooth Status and private fixture AA:BB:CC:DD:EE:FF key=123456')
        print('shim::btm  2026-10-14 16:29:00.000 CBACK Bond state changed : AA:BB:CC:DD:EE:FF bt_status:auth_failure bond_state:0 reason:HCI_ERR_KEY_MISSING')
        print(' ----- shim::btm -----')
        print('shim::btm  2026-10-14 16:29:50.000 CBACK Bond state changed : AA:BB:CC:DD:EE:FF bt_status:success bond_state:1 reason:HCI_SUCCESS')
        print('shim::btm  2026-10-14 16:29:57.000 CBACK Bond state changed : AA:BB:CC:DD:EE:FF bt_status:auth_failure bond_state:0 reason:HCI_ERR_AUTH_FAILURE')
        print('shim::btm  2026-10-14 16:29:58.000 API Remove bond : AA:BB:CC:DD:EE:FF PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY')
        print('shim::btm  2026-10-14 16:29:59.000 CBACK Ssp request : AA:BB:CC:DD:EE:FF pin:123456')
        print('shim::btm  2026-10-14 15:29:00.000 CBACK Bond state changed : AA:BB:CC:DD:EE:FF bt_status:auth_failure bond_state:0 reason:HCI_ERR_KEY_MISSING')
        print('shim::btm  2026-10-14 16:31:00.000 CBACK Bond state changed : AA:BB:CC:DD:EE:FF bt_status:auth_failure bond_state:0 reason:HCI_ERR_KEY_MISSING')
        print('shim::btm  2026-10-14 16:30:00.500 CBACK Bond state changed : AA:BB:CC:DD:EE:FF bt_status:auth_failure bond_state:0 reason:HCI_ERR_KEY_MISSING')
        print(' ----- another native section -----')
        print('shim::btm  2026-10-14 16:29:00.000 CBACK Bond state changed : AA:BB:CC:DD:EE:FF bt_status:auth_failure bond_state:0 reason:HCI_ERR_KEY_MISSING')
elif args[:2] == ['-d', 'logcat']:
    if case == 'logcat_error': sys.exit(1)
    if case == 'snapshot':
        assert args[2:4] == ['-d','--pid=1234']
        print('--------- beginning of main')
        print('1791999990.000 1234 1234 I BluetoothBondStateMachine: [prefix] bondStateChangeCallback status=0 new_state=0x01 hci_reason=0x00 AA:BB:CC:DD:EE:FF')
        print('1791999995.000 1234 1234 W bt_btif_dm: Bonding failed with failure reason:HCI_ERR_AUTH_FAILURE AA:BB:CC:DD:EE:FF PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY')
        print('1791999090.000 1234 1234 I BluetoothBondStateMachine: bondStateChangeCallback: Status: 1 newState: 0 hciReason: 6')
        print('1792000001.000 1234 1234 I BluetoothBondStateMachine: bondStateChangeCallback: Status: 1 newState: 0 hciReason: 8')
        print('1791999998.000 1234 1234 I BluetoothBondStateMachine: PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY')
    elif case == 'native_failure':
        filters = args[args.index('-s')+1:]
        if 'bt_btif_dm:I' in filters:
            print('Bonding failed with failure reason:HCI_ERR_KEY_MISSING AA:BB:CC:DD:EE:FF PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY', flush=True)
        time.sleep(5)
    elif case == 'prefixed_tag':
        # Model logcat tag filtering, rather than bypassing it with raw fixture output.
        filters = args[args.index('-s')+1:]
        if 'BluetoothBondStateMachine:I' in filters:
            print('bondStateChangeCallback: Status: 0 Address: AA:BB:CC:DD:EE:FF newState: 1 hciReason: 0', flush=True)
            print('bondStateChangeCallback: Status: 1 Address: AA:BB:CC:DD:EE:FF newState: 0 hciReason: 5', flush=True)
        time.sleep(5)
    elif case == 'no_events':
        print('PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY', flush=True)
        print('bondStateChangeCallback: Status: 999 Address: secret newState: 0 hciReason: 999', flush=True)
        time.sleep(5)
    elif case == 'flood':
        for _ in range(200):
            print('bondStateChangeCallback: Status: 0 Address: XX:XX:XX:XX:EE:FF newState: 1 hciReason: 0', flush=True)
        time.sleep(5)
    else:
        print('bondStateChangeCallback: Status: 0 Address: AA:BB:CC:DD:EE:FF newState: 1 hciReason: 0', flush=True)
        print('PRIVATE_FIXTURE_MUST_NOT_LEAVE_MEMORY', flush=True)
        print('bondStateChangeCallback: Status: 1 Address: AA:BB:CC:DD:EE:FF newState: 0 hciReason: 5', flush=True)
        time.sleep(5)
else: sys.exit(3)
'''


def psquote(value):
    return "'" + str(value).replace("'", "''") + "'"


class CollectorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='switchphone-collector-')
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.fake = self.folder / 'fake-adb'
        self.fake.write_text(FAKE)
        self.fake.chmod(0o755)

    def run_case(self, case, recent=False, service=False):
        output = self.folder / 'numeric.json'
        calls = self.folder / 'args.jsonl'
        runner = self.folder / 'run.ps1'
        runner.write_text(
            '. ' + psquote(SCRIPT) + '\ntry {\n'
            '$result = ' + ('Invoke-BluetoothServiceHistory ' if service else 'Invoke-BondSnapshot ' if recent else 'Invoke-BondCapture ') + psquote(self.fake) + ('' if recent or service else ' -Seconds 1') + '\n'
            '$result | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 '
            + psquote(output) + '\n} catch { Write-Host $_.Exception.Message; exit 2 }\n')
        env = dict(os.environ, SWITCHPHONE_ADB_CASE=case, SWITCHPHONE_ADB_ARGS=str(calls))
        process = subprocess.run([PWSH, '-NoLogo', '-NoProfile', '-File', str(runner)],
                                 env=env, capture_output=True, text=True, timeout=15)
        text = process.stdout + process.stderr
        self.assertNotIn(SENTINEL, text)
        self.assertNotIn('AA:BB:CC:DD:EE:FF', text)
        self.assertNotIn('key=123456', text)
        data = json.loads(output.read_text(encoding='utf-8-sig')) if output.exists() else None
        if data is not None:
            saved = output.read_text(encoding='utf-8-sig')
            self.assertNotIn(SENTINEL, saved)
            self.assertNotIn('AA:BB:CC:DD:EE:FF', saved)
            for event in data['events']:
                self.assertEqual(set(event), {'elapsed_seconds','status','native_bond_state','hci_reason'})
                self.assertTrue(all(isinstance(v, int) for v in event.values()))
            for event in data['native_failures']:
                self.assertEqual(set(event), {'elapsed_seconds','hci_reason'})
                self.assertTrue(all(isinstance(v, int) for v in event.values()))
            self.assertTrue(all(isinstance(v,int) for v in data['marker_counts'].values()))
        argv = [json.loads(line) for line in calls.read_text().splitlines()]
        return process, data, argv

    def test_terminal_bond_and_privacy(self):
        process, data, argv = self.run_case('normal')
        self.assertEqual(process.returncode, 0, process.stdout + process.stderr)
        self.assertEqual(data['outcome'], 'bond_events')
        self.assertEqual(data['collector_version'], '1.3.0')
        self.assertEqual((data['lines_read'],data['unmatched_lines']), (3,1))
        self.assertEqual([(e['native_bond_state'],e['hci_reason']) for e in data['events']], [(1,0),(0,5)])
        self.assertEqual(argv[-1], ['-d','logcat','--pid=1234','-b','main','-v','raw','-T',
                                   '1792000000.123456789','-s','BluetoothBondStateMachine:I','BondStateMachine:I','bt_btif_dm:I','*:S'])
        self.assertTrue(all('-c' not in command for command in argv))

    def test_empty_capture_expires(self):
        process, data, _ = self.run_case('no_events')
        self.assertEqual(process.returncode, 0, process.stdout + process.stderr)
        self.assertEqual(data['outcome'], 'no_events')
        self.assertEqual(data['events'], [])
        self.assertEqual((data['lines_read'],data['unmatched_lines']), (2,2))
        self.assertLessEqual(data['duration_seconds'], 2)

    def test_android16_prefixed_logcat_tag(self):
        process, data, _ = self.run_case('prefixed_tag')
        self.assertEqual(process.returncode, 0, process.stdout + process.stderr)
        self.assertEqual(data['outcome'], 'bond_events')
        self.assertEqual([e['hci_reason'] for e in data['events']], [0,5])

    def test_recent_snapshot_preserves_age_and_rejects_outside_window(self):
        process, data, argv = self.run_case('snapshot',recent=True)
        self.assertEqual(process.returncode, 0, process.stdout + process.stderr)
        self.assertEqual(data['capture_mode'],'recent_buffer')
        self.assertEqual(data['lookback_seconds'],900)
        self.assertEqual(len(data['events']),1)
        self.assertEqual(data['events'][0],dict(elapsed_seconds=-11,status=0,native_bond_state=1,hci_reason=0))
        self.assertEqual(data['native_failures'],[dict(elapsed_seconds=-6,hci_reason=5)])
        self.assertEqual(data['outside_window_lines'],2)
        self.assertEqual(data['marker_counts']['callback_marker'],3)
        self.assertIn('1791999100.000',argv[-1])
        self.assertIn('epoch',argv[-1])
        self.assertIn('-d',argv[-1][2:])

    def test_native_failure_without_java_callback(self):
        process, data, _ = self.run_case('native_failure')
        self.assertEqual(process.returncode,0,process.stdout+process.stderr)
        self.assertEqual(data['outcome'],'native_failure')
        self.assertEqual(data['events'],[])
        self.assertEqual([e['hci_reason'] for e in data['native_failures']],[6])
        self.assertEqual(data['marker_counts']['callback_marker'],0)
        self.assertEqual(data['marker_counts']['native_failure_marker'],1)

    def test_btm_service_history_filters_section_age_and_private_fields(self):
        process,data,argv=self.run_case('service',service=True)
        self.assertEqual(process.returncode,0,process.stdout+process.stderr)
        self.assertEqual(data['capture_mode'],'bluetooth_service_history')
        self.assertTrue(data['native_history_header_seen'])
        self.assertEqual(data['lookback_seconds'],3600)
        self.assertEqual(data['events'],[dict(elapsed_seconds=-10,status=0,native_bond_state=1,hci_reason=0),
                                         dict(elapsed_seconds=-3,status=9,native_bond_state=0,hci_reason=5)])
        self.assertEqual(data['requests'],[dict(elapsed_seconds=-2,operation=3)])
        self.assertEqual(data['outside_window_lines'],3)
        self.assertEqual(data['unmatched_lines'],1)
        self.assertEqual(argv[-1],['-d','shell','dumpsys','bluetooth_manager'])
        self.assertEqual(len(argv),3)

    def test_service_dump_permission_denial_is_not_empty_success(self):
        process,data,_=self.run_case('service_denied',service=True)
        self.assertEqual(process.returncode,2)
        self.assertIsNone(data)
        self.assertIn('E_SERVICE_DUMP',process.stdout)

    def test_bounded_event_count(self):
        process, data, _ = self.run_case('flood')
        self.assertEqual(process.returncode, 0, process.stdout + process.stderr)
        self.assertEqual(len(data['events']), 64)

    def test_preflight_and_stream_failures(self):
        for case, code, count in [('unauthorized','E_USB',1),('bad_pid','E_BT_PID',2),
                                   ('bad_clock','E_CLOCK',3),('logcat_error','E_LOGCAT',4)]:
            with self.subTest(case=case):
                # Each subcase uses separate call/output files.
                for name in ('args.jsonl','numeric.json'):
                    (self.folder / name).unlink(missing_ok=True)
                process, data, argv = self.run_case(case)
                self.assertEqual(process.returncode, 2)
                self.assertIn(code, process.stdout)
                self.assertIsNone(data)
                self.assertEqual(len(argv), count)

    def test_parser_rejects_unexpected_content(self):
        runner = self.folder / 'parser.ps1'
        runner.write_text('. ' + psquote(SCRIPT) + r'''
$valid = Convert-BondLine 'bondStateChangeCallback: Status: 0 Address: XX:XX:XX:XX:EE:FF newState: 2 hciReason: -1' 3
if ($null -eq $valid -or $valid.hci_reason -ne -1 -or $valid.elapsed_seconds -ne 3) { throw 'valid event lost' }
$variant = Convert-BondLine '[prefix] bondStateChangeCallback(status=0x01, new_state=0, hci_reason=0x05) key=123456' 2
if ($null -eq $variant -or $variant.hci_reason -ne 5 -or $variant.status -ne 1) { throw 'variant event lost' }
$invalid = @(
    'key=123456',
    'bondStateChangeCallback: Status: 256 Address: private newState: 0 hciReason: 0',
    'bondStateChangeCallback: Status: 1 Address: private newState: 3 hciReason: 0',
    'bondStateChangeCallback: Status: 1 Address: private newState: 0 hciReason: 256',
    'bondStateChangeCallback: Status: 1 Status: 0 newState: 0 hciReason: 5',
    'otherFunction: Status: 1 newState: 0 hciReason: 5',
    "bondStateChangeCallback: Status: 1 Address: private`nkey=123456 newState: 0 hciReason: 5"
)
foreach ($line in $invalid) { if ($null -ne (Convert-BondLine $line 0)) { throw 'unexpected event accepted' } }
$native = Convert-NativeFailure 'file.cc: Authentication fail:0x0e key=123456' 4
if ($null -eq $native -or $native.hci_reason -ne 14) { throw 'native hex lost' }
if ($null -ne (Convert-NativeFailure 'Bonding failed with failure reason:PRIVATE_UNKNOWN_LABEL' 0)) { throw 'unknown native label accepted' }
if ($null -ne (Convert-NativeFailure 'Bonding failed with failure reason:999' 0)) { throw 'out of range native code accepted' }
$btm = Convert-BtmBondLine 'shim::btm  2026-10-14 16:29:00.000 CBACK Bond state changed : private bt_status:UNKNOWN bond_state:0 reason:HCI_ERR_AUTH_FAILURE' -5
if ($null -ne $btm) { throw 'unknown BTM status invented' }
if ($null -ne (Convert-BtmBondLine 'shim::btm  time CBACK Bond state changed : private bt_status:success bond_state:0 reason:HCI_SUCCESS reason:HCI_ERR_AUTH_FAILURE' -5)) { throw 'ambiguous BTM reason accepted' }
Write-Host 'Parser checks passed'
''')
        process = subprocess.run([PWSH, '-NoLogo', '-NoProfile', '-File', str(runner)],
                                 capture_output=True, text=True, timeout=10)
        self.assertEqual(process.returncode, 0, process.stdout + process.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
