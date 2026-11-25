import 'package:flutter_test/flutter_test.dart';

import 'package:bari/main.dart';

void main() {
  testWidgets('displays the storage permission status from the native channel', (
    tester,
  ) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(storageChannel, (call) async {
          expect(call.method, 'getStoragePermissionStatus');
          return true;
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(storageChannel, null),
    );

    await tester.pumpWidget(const BariApp());
    await tester.pumpAndSettle();

    expect(find.text('Granted'), findsOneWidget);
  });
}
