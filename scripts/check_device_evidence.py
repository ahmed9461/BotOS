#!/usr/bin/env python3
"""Require genuine screenshots and real-IME measurements before APK publication."""
from pathlib import Path
import math
import shutil
import struct
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
outputs = root / 'app/build/outputs'
destination = root / 'diagnostics/ui/screenshots'
destination.mkdir(parents=True, exist_ok=True)
required = ['appearance-light-ar.png', 'appearance-dark-ar.png', 'appearance-restored-ar.png',
            'workspace-ar.png', 'keyboard-ar.png', 'library-ar.png', 'editor-ar.png', 'account-unconfigured-ar.png',
            'rich-chat-light-ar.png', 'rich-chat-dark-ar.png', 'rich-chat-switcher-ar.png', 'avatars-library-ar.png', 'streaming-reply-ar.png', 'received-media-light-ar.png', 'received-media-dark-ar.png', 'received-sticker-ar.png', 'received-video-ar.png', 'received-webm-ar.png', 'outgoing-preview-light-ar.png', 'outgoing-preview-dark-ar.png', 'keyboard-gap.txt']
for name in required:
    matches = [p for p in outputs.rglob(name) if p.is_file() and 'additional_output' in str(p)]
    if len(matches) != 1:
        raise SystemExit(f'Expected one collected test output {name}, got {len(matches)}')
    data = matches[0].read_bytes()
    if name.endswith('.png'):
        if data[:8] != b'\x89PNG\r\n\x1a\n' or len(data) < 24:
            raise SystemExit(f'Invalid PNG evidence: {name}')
        width, height = struct.unpack('>II', data[16:24])
        if width < 300 or height < 300:
            raise SystemExit(f'Unexpected screenshot dimensions: {name}: {width}x{height}')
        print(f'{name}: {width}x{height}')
    else:
        fields = dict(line.split('=', 1) for line in data.decode().splitlines() if '=' in line)
        gap = float(fields['gapDp'])
        if not math.isfinite(gap) or not -2 <= gap <= 12:
            raise SystemExit(f'Keyboard gap outside accepted range: {gap}dp')
        print(f'Real keyboard gap: {gap} dp')
    shutil.copyfile(matches[0], destination / name)
reports = list((outputs / 'androidTest-results').rglob('TEST-*.xml'))
suites = [ET.parse(path).getroot() for path in reports]
assert sum(int(s.get('tests', 0)) for s in suites) == 70, 'Expected 62 prior app cases and 8 attachment preparation/preview cases'
assert all(int(s.get(k, 0)) == 0 for s in suites for k in ('failures', 'errors', 'skipped')), 'App device test did not pass'
outgoing_cases = {case.get('name') for suite in suites for case in suite.iter('testcase')
                  if case.get('classname') == 'com.ahmed9461.botos.OutgoingRetentionTest'}
assert outgoing_cases == {
    'stagedInputSurvivesReopenAndCancelledPreviewIsRemoved',
    'journalIsDurableBeforeRpcAndUnknownCannotBeDeletedOrSentTwice',
    'terminalBeforeLatePendingNeverRegressesAndOnlySuccessReleasesFile',
    'failedFinalStateRetainsFileAndCannotBeTreatedAsSuccess',
    'corruptionFailsClosedWithoutDiscardingRetainedInput',
    'fileCountLimitDoesNotEvictUncertainOrPendingMedia',
    'missingOrForeignFileCannotReachAttemptedState',
    'namedInputKeepsSafeExtensionAndLegacyFileStillResolves',
    'restartRemovesOnlyUnreservedPreviewAndKeepsUncertainUpload',
}, 'Expected all nine outgoing retention cases'
coordination_cases = {case.get('name') for suite in suites for case in suite.iter('testcase')
                      if case.get('classname') == 'com.ahmed9461.botos.TelegramUploadsTest'}
assert coordination_cases == {
    'queuePersistsAttemptBeforeRpcAndFinalUpdateReleasesFile',
    'sameStagedFileCannotBeQueuedTwiceOrRetriedOnUncertainResponse',
    'finalUpdateBeforeRpcResponseCannotRestorePendingOrResend',
    'interruptedRpcRetainsUncertainFileForExplicitReview',
    'staleTargetAndWrongAccountOrChatUpdateCannotAffectAnotherUpload',
    'restartProbesKnownTemporaryIdForSameUserWithoutResending',
}, 'Expected all six account-scoped upload coordination cases'
preparation_cases = {case.get('name') for suite in suites for case in suite.iter('testcase')
                     if case.get('classname') == 'com.ahmed9461.botos.media.OutgoingAttachmentPreparerTest'}
assert preparation_cases == {
    'selectedPhotoIsReencodedBeforeOneDurableSendAndReleasedOnFinalUpdate',
    'mp4H264IsVideoAndWebmRequiresExplicitFilePreview',
    'invalidPhotoCannotLeaveAPreviewOrJournalRecord',
    'knownM4aIsAudioWithDurationAndUnknownAudioIsExplicitlyAFile',
}, 'Expected all four attachment preparation cases'
assert any(case.get('name') == 'previewShowsActualPixelsTargetCaptionAndCancellationInLightAndDarkRtl'
           for suite in suites for case in suite.iter('testcase')), 'Expected preview and cancellation UI case'
assert any(case.get('name') == 'attachmentMenuOffersOnlyChosenKindsAndDoesNotSendOnOpening'
           for suite in suites for case in suite.iter('testcase')), 'Expected live composer attachment menu case'
print('All twenty device screenshots, seventy app tests and keyboard measurement verified.')
