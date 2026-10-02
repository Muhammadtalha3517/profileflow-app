import 'package:flutter/material.dart';
import '../services/autofill_channel_service.dart';
import '../services/secure_storage_service.dart';

/// Screen 2: "Autofill Setup"
/// 
/// Steps + buttons:
/// - open Settings (Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
/// - check if ProfileFlow is the active service
/// - instructions for enabling "Autofill using another service" in Chrome settings
/// - show status (enabled / not enabled)
class AutofillSetupScreen extends StatefulWidget {
  const AutofillSetupScreen({super.key});

  @override
  State<AutofillSetupScreen> createState() => _AutofillSetupScreenState();
}

class _AutofillSetupScreenState extends State<AutofillSetupScreen>
    with WidgetsBindingObserver {
  bool _isChecking = true;
  bool _isActiveService = false;
  bool _isSupported = true;
  bool _requireBiometrics = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // Re-check service status whenever user returns from Android Settings
      _checkStatus();
    }
  }

  Future<void> _checkStatus() async {
    setState(() => _isChecking = true);
    final supported = await AutofillChannelService.isAutofillSupported();
    final active = await AutofillChannelService.isAutofillServiceActive();
    final bioRequired = await SecureStorageService.getBiometricSetting();

    if (mounted) {
      setState(() {
        _isSupported = supported;
        _isActiveService = active;
        _requireBiometrics = bioRequired;
        _isChecking = false;
      });
    }
  }

  Future<void> _handleOpenSettings() async {
    final success = await AutofillChannelService.openAutofillSettings();
    if (!success && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Could not open Android Autofill Settings automatically.'),
        ),
      );
    }
  }

  Future<void> _toggleBiometrics(bool value) async {
    setState(() => _requireBiometrics = value);
    await SecureStorageService.setBiometricSetting(value);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Autofill Setup'),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh Status',
            onPressed: _checkStatus,
          ),
        ],
      ),
      body: _isChecking
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16.0),
              children: [
                // Top Service Status Card
                _buildStatusBanner(),
                const SizedBox(height: 20),

                // Step 1: Android System Autofill Service
                _buildStepCard(
                  stepNumber: 1,
                  title: 'Set ProfileFlow as Default Provider',
                  description:
                      'Android requires selecting ProfileFlow as your primary autofill service in system settings.',
                  actionWidget: ElevatedButton.icon(
                    onPressed: _handleOpenSettings,
                    icon: const Icon(Icons.settings_suggest),
                    label: Text(
                      _isActiveService
                          ? 'Change Autofill Service'
                          : 'Open System Settings',
                    ),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 12,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Step 2: Google Chrome Configuration
                _buildStepCard(
                  stepNumber: 2,
                  title: 'Enable 3rd-Party Autofill in Chrome',
                  description:
                      'Chrome on Android uses Google Autofill by default. Follow these steps to allow ProfileFlow:',
                  childContent: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildBulletItem(
                        '1. Open Google Chrome on your phone.',
                      ),
                      _buildBulletItem(
                        '2. Tap the three dots (⋮) menu > Settings > Autofill services.',
                      ),
                      _buildBulletItem(
                        '3. Select "Autofill using another service".',
                      ),
                      _buildBulletItem(
                        '4. Confirm when Chrome prompts to use ProfileFlow.',
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: Colors.blue.withAlpha(25),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: Colors.blue.withAlpha(60)),
                        ),
                        child: const Row(
                          children: [
                            Icon(Icons.info_outline, size: 20, color: Colors.blue),
                            SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Tip: In older Chrome builds, visit chrome://flags and enable #enable-autofill-virtual-view-structure.',
                                style: TextStyle(fontSize: 12, color: Colors.blue),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Step 3: Biometric & Security Gate
                _buildStepCard(
                  stepNumber: 3,
                  title: 'Biometric & Privacy Protection',
                  description:
                      'Require fingerprint, face unlock, or device PIN before releasing any field to Chrome.',
                  childContent: SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text(
                      'Require Biometric Authentication',
                      style: TextStyle(fontWeight: FontWeight.w600),
                    ),
                    subtitle: const Text(
                      'Dataset values are locked until you scan your finger.',
                    ),
                    value: _requireBiometrics,
                    onChanged: _toggleBiometrics,
                  ),
                ),
                const SizedBox(height: 24),

                // Security Guarantee Footer
                _buildSecurityFooter(),
              ],
            ),
    );
  }

  Widget _buildStatusBanner() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _isActiveService ? const Color(0xFFE8F5E9) : const Color(0xFFFFF3E0),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: _isActiveService ? const Color(0xFF81C784) : const Color(0xFFFFB74D),
        ),
      ),
      child: Row(
        children: [
          Icon(
            _isActiveService ? Icons.check_circle : Icons.warning_amber_rounded,
            color: _isActiveService ? const Color(0xFF2E7D32) : const Color(0xFFE65100),
            size: 32,
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _isActiveService
                      ? 'ProfileFlow is Active'
                      : 'Autofill Not Enabled',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: _isActiveService
                        ? const Color(0xFF1B5E20)
                        : const Color(0xFFBF360C),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  _isActiveService
                      ? 'Ready to autofill profile fields in Chrome and native apps.'
                      : 'Tap Step 1 below to enable ProfileFlow in Android settings.',
                  style: TextStyle(
                    fontSize: 13,
                    color: _isActiveService
                        ? const Color(0xFF2E7D32)
                        : const Color(0xFFE65100),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStepCard({
    required int stepNumber,
    required String title,
    required String description,
    Widget? actionWidget,
    Widget? childContent,
  }) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.grey.shade300),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 13,
                  backgroundColor: Theme.of(context).primaryColor,
                  child: Text(
                    '$stepNumber',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              description,
              style: TextStyle(fontSize: 14, color: Colors.grey.shade700),
            ),
            if (childContent != null) ...[
              const SizedBox(height: 12),
              childContent,
            ],
            if (actionWidget != null) ...[
              const SizedBox(height: 14),
              actionWidget,
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildBulletItem(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('• ', style: TextStyle(fontWeight: FontWeight.bold)),
          Expanded(
            child: Text(
              text,
              style: TextStyle(fontSize: 13, color: Colors.grey.shade800),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSecurityFooter() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(Icons.shield_outlined, color: Colors.grey.shade700, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Zero-Cloud Privacy: All data stays strictly on your device. Never auto-submits, never interacts with CAPTCHAs. You always review before pressing submit.',
              style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
            ),
          ),
        ],
      ),
    );
  }
}
