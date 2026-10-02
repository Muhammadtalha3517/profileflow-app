import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/user_profile.dart';
import '../services/secure_storage_service.dart';

/// Screen 3: "Copy helper"
/// 
/// Lists each profile field with a one-tap Copy button.
/// Enforces strict privacy: Clipboard auto-clears after 30 seconds
/// for fields autofill can't detect (bio, skills, experience, etc.).
class CopyHelperScreen extends StatefulWidget {
  const CopyHelperScreen({super.key});

  @override
  State<CopyHelperScreen> createState() => _CopyHelperScreenState();
}

class _CopyHelperScreenState extends State<CopyHelperScreen> {
  UserProfile _profile = UserProfile();
  bool _isLoading = true;
  String _searchQuery = '';

  // 30-Second Auto-Clear State
  Timer? _clipboardTimer;
  int _secondsRemaining = 0;
  String? _lastCopiedFieldName;

  @override
  void initState() {
    super.initState();
    _loadProfile();
  }

  @override
  void dispose() {
    _clipboardTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadProfile() async {
    setState(() => _isLoading = true);
    final profile = await SecureStorageService.loadProfile();
    if (mounted) {
      setState(() {
        _profile = profile;
        _isLoading = false;
      });
    }
  }

  /// Copies field value to clipboard and starts strict 30-second auto-clear timer
  void _copyToClipboard(String label, String value) {
    if (value.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$label is empty in your profile.')),
      );
      return;
    }

    Clipboard.setData(ClipboardData(text: value));

    _clipboardTimer?.cancel();
    setState(() {
      _lastCopiedFieldName = label;
      _secondsRemaining = 30;
    });

    _clipboardTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }

      setState(() {
        if (_secondsRemaining > 1) {
          _secondsRemaining--;
        } else {
          // Time expired! Wipe clipboard immediately
          _wipeClipboard();
          timer.cancel();
        }
      });
    });

    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Copied $label! Clipboard will auto-clear in 30s.'),
        duration: const Duration(seconds: 3),
        action: SnackBarAction(
          label: 'Clear Now',
          onPressed: _wipeClipboard,
        ),
      ),
    );
  }

  void _wipeClipboard() {
    Clipboard.setData(const ClipboardData(text: ''));
    _clipboardTimer?.cancel();
    if (mounted) {
      setState(() {
        _secondsRemaining = 0;
        _lastCopiedFieldName = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Clipboard cleared securely.'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Copy Helper'),
        elevation: 0,
        actions: [
          if (_secondsRemaining > 0)
            IconButton(
              icon: const Icon(Icons.cleaning_services),
              tooltip: 'Clear Clipboard Now',
              onPressed: _wipeClipboard,
            ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                // Top Clipboard Security Countdown Bar
                if (_secondsRemaining > 0) _buildAutoClearBanner(),

                // Search field
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: TextField(
                    decoration: InputDecoration(
                      hintText: 'Search fields (e.g. bio, skills, address)...',
                      prefixIcon: const Icon(Icons.search),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                      contentPadding: const EdgeInsets.symmetric(vertical: 10),
                    ),
                    onChanged: (val) => setState(() => _searchQuery = val.toLowerCase()),
                  ),
                ),

                // Field Lists
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      // Section 1: Complex Unmatchable Fields (Primary purpose of Copy Helper)
                      _buildSectionHeader(
                        'Unmatchable & Complex Fields',
                        'Fields web autofill cannot classify (Upwork bio, skill lists, experience summaries)',
                      ),
                      _buildFieldTile('Professional Bio', _profile.bio, isMultiLine: true),
                      _buildFieldTile('Core Skills', _profile.skills, isMultiLine: true),
                      _buildFieldTile('Work Experience', _profile.experience, isMultiLine: true),
                      _buildFieldTile('Portfolio URL', _profile.portfolioUrl),
                      _buildFieldTile('LinkedIn Profile', _profile.linkedInUrl),

                      const SizedBox(height: 20),

                      // Section 2: Standard Profile Fields (Quick tap-to-copy fallback)
                      _buildSectionHeader(
                        'Standard Form Fields',
                        'Quick one-tap manual copy if Chrome autofill does not trigger',
                      ),
                      _buildFieldTile('First Name', _profile.firstName),
                      _buildFieldTile('Last Name', _profile.lastName),
                      _buildFieldTile('Full Name', _profile.fullName),
                      _buildFieldTile('Email Address', _profile.email),
                      _buildFieldTile('Password', _profile.password, isSensitive: true),
                      _buildFieldTile('Phone Number', _profile.phone),
                      _buildFieldTile('Street Address', _profile.address),
                      _buildFieldTile('City', _profile.city),
                      _buildFieldTile('Country', _profile.country),
                      _buildFieldTile('Postal / ZIP Code', _profile.postalCode),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildAutoClearBanner() {
    final progress = _secondsRemaining / 30.0;
    return Container(
      color: Colors.amber.shade100,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Column(
        children: [
          Row(
            children: [
              const Icon(Icons.timer, color: Colors.brown, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Clipboard holds "$_lastCopiedFieldName" — wipes in ${_secondsRemaining}s',
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.bold,
                    color: Colors.brown,
                  ),
                ),
              ),
              TextButton(
                onPressed: _wipeClipboard,
                child: const Text('Clear Now', style: TextStyle(color: Colors.red)),
              ),
            ],
          ),
          const SizedBox(height: 4),
          LinearProgressIndicator(
            value: progress,
            backgroundColor: Colors.amber.shade300,
            valueColor: const AlwaysStoppedAnimation<Color>(Colors.brown),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title, String subtitle) {
    return Padding(
      padding: const EdgeInsets.only(top: 8, bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
          ),
          Text(
            subtitle,
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
          ),
          const Divider(),
        ],
      ),
    );
  }

  Widget _buildFieldTile(String label, String value, {bool isMultiLine = false, bool isSensitive = false}) {
    if (_searchQuery.isNotEmpty &&
        !label.toLowerCase().contains(_searchQuery) &&
        !value.toLowerCase().contains(_searchQuery)) {
      return const SizedBox.shrink();
    }

    final hasValue = value.trim().isNotEmpty;
    final displayValue = isSensitive && hasValue
        ? '••••••••••••'
        : hasValue
            ? value
            : 'Not set in profile';

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: Colors.grey.shade200),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        child: Row(
          crossAxisAlignment: isMultiLine ? CrossAxisAlignment.start : CrossAxisAlignment.center,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: Colors.grey.shade700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    displayValue,
                    maxLines: isMultiLine ? 4 : 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 14,
                      color: hasValue ? Colors.black87 : Colors.grey.shade400,
                      fontFamily: isSensitive ? 'monospace' : null,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 10),
            ElevatedButton.icon(
              onPressed: hasValue ? () => _copyToClipboard(label, value) : null,
              icon: const Icon(Icons.copy, size: 16),
              label: const Text('Copy'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
