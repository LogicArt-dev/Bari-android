import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';

const storageChannel = MethodChannel('com.logicart.bari/storage');

Future<bool> getStoragePermissionStatus() {
  return storageChannel.invokeMethod<bool>('getStoragePermissionStatus').then(
    (status) => status ?? false,
  );
}

void main() {
  runApp(const BariApp());
}

class BariApp extends StatelessWidget {
  const BariApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(home: StoragePermissionStatusScreen());
  }
}

class StoragePermissionStatusScreen extends StatefulWidget {
  const StoragePermissionStatusScreen({super.key});

  @override
  State<StoragePermissionStatusScreen> createState() =>
      _StoragePermissionStatusScreenState();
}

class _StoragePermissionStatusScreenState
    extends State<StoragePermissionStatusScreen> {
  late final Future<bool> _status = getStoragePermissionStatus();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Storage permission status')),
      body: Center(
        child: FutureBuilder<bool>(
          future: _status,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return Text('Error checking status: ${snapshot.error}');
            }
            if (snapshot.connectionState != ConnectionState.done) {
              return const CircularProgressIndicator();
            }
            return Text(
              snapshot.data! ? 'Granted' : 'Not granted',
              key: const ValueKey('storage-permission-status'),
            );
          },
        ),
      ),
    );
  }
}
