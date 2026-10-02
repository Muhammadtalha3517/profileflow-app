import 'package:flutter/services.dart';

/// Flutter service wrapping Android MethodChannel communication
/// for Autofill management and secure data synchronization.
class AutofillChannelService {
  static const MethodChannel _channel =
      MethodChannel('com.profileflow.assistant/autofill');

  /// Checks if the host Android OS supports AutofillService (API 26+)
  static Future<bool> isAutofillSupported() async {
    try {
      final bool? supported =
          await _channel.invokeMethod<bool>('isAutofillSupported');
      return supported ?? false;
    } on PlatformException catch (_) {
      return false;
    }
  }

  /// Checks whether ProfileFlow is currently selected as the active Autofill Provider in Android settings
  static Future<bool> isAutofillServiceActive() async {
    try {
      final bool? isActive =
          await _channel.invokeMethod<bool>('isAutofillServiceActive');
      return isActive ?? false;
    } on PlatformException catch (_) {
      return false;
    }
  }

  /// Triggers Android's native Settings intent:
  /// Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE with package Uri
  static Future<bool> openAutofillSettings() async {
    try {
      final bool? result =
          await _channel.invokeMethod<bool>('openAutofillSettings');
      return result ?? false;
    } on PlatformException catch (_) {
      return false;
    }
  }

  /// Syncs user profile fields securely to native EncryptedSharedPreferences/Keystore
  /// so ProfileFlowAutofillService can read it onFillRequest without waking up Flutter engine.
  static Future<bool> syncProfileToNativeStorage({
    required Map<String, String> profileData,
    required bool isBiometricRequired,
  }) async {
    try {
      final bool? success = await _channel.invokeMethod<bool>(
        'syncProfileToNativeStorage',
        {
          'profileData': profileData,
          'isBiometricRequired': isBiometricRequired,
        },
      );
      return success ?? false;
    } on PlatformException catch (_) {
      return false;
    }
  }

  /// Updates biometric requirement setting in native keystore
  static Future<bool> setBiometricRequirement(bool required) async {
    try {
      final bool? success = await _channel.invokeMethod<bool>(
        'setBiometricRequirement',
        {'required': required},
      );
      return success ?? false;
    } on PlatformException catch (_) {
      return false;
    }
  }
}
