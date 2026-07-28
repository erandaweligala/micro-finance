import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/error/api_exception.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/utils/validators.dart';
import '../../../core/widgets/common_widgets.dart';
import '../data/customer_repository.dart';
import '../domain/customer.dart';

/// Customer registration and editing.
///
/// Server-side field errors are mapped back onto the individual inputs, so a
/// rejected national id highlights that field rather than showing an opaque
/// banner the user has to interpret.
class CustomerFormScreen extends ConsumerStatefulWidget {
  const CustomerFormScreen({this.customerId, super.key});

  /// Null for registration, set when editing an existing customer.
  final String? customerId;

  @override
  ConsumerState<CustomerFormScreen> createState() => _CustomerFormScreenState();
}

class _CustomerFormScreenState extends ConsumerState<CustomerFormScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();

  final TextEditingController _firstName = TextEditingController();
  final TextEditingController _middleName = TextEditingController();
  final TextEditingController _lastName = TextEditingController();
  final TextEditingController _nationalId = TextEditingController();
  final TextEditingController _phone = TextEditingController();
  final TextEditingController _email = TextEditingController();
  final TextEditingController _addressLine = TextEditingController();
  final TextEditingController _city = TextEditingController();
  final TextEditingController _occupation = TextEditingController();
  final TextEditingController _employer = TextEditingController();
  final TextEditingController _income = TextEditingController();

  DateTime? _dateOfBirth;
  String _idType = 'NATIONAL_ID';
  String? _gender;
  String? _maritalStatus;

  bool _isSubmitting = false;
  bool _isLoading = false;
  String? _formError;
  Map<String, String> _fieldErrors = <String, String>{};

  bool get _isEditing => widget.customerId != null;

  @override
  void initState() {
    super.initState();
    if (_isEditing) {
      _loadExisting();
    }
  }

  Future<void> _loadExisting() async {
    setState(() => _isLoading = true);
    try {
      final Customer customer =
          await ref.read(customerRepositoryProvider).get(widget.customerId!);
      if (!mounted) {
        return;
      }
      setState(() {
        _firstName.text = customer.firstName ?? '';
        _middleName.text = customer.middleName ?? '';
        _lastName.text = customer.lastName ?? '';
        _email.text = customer.email ?? '';
        _addressLine.text = customer.address?.line1 ?? '';
        _city.text = customer.address?.city ?? '';
        _occupation.text = customer.occupation ?? '';
        _employer.text = customer.employer ?? '';
        _income.text = customer.monthlyIncome ?? '';
        _dateOfBirth = customer.dateOfBirth;
        _idType = customer.idType ?? 'NATIONAL_ID';
        _gender = customer.gender;
        _maritalStatus = customer.maritalStatus;
        // Identification and phone numbers come back masked and are therefore
        // left blank: submitting the mask would overwrite the real value.
      });
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _formError = error.message);
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  void dispose() {
    for (final TextEditingController controller in <TextEditingController>[
      _firstName, _middleName, _lastName, _nationalId, _phone, _email,
      _addressLine, _city, _occupation, _employer, _income,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _pickDateOfBirth() async {
    final DateTime now = DateTime.now();
    final DateTime? picked = await showDatePicker(
      context: context,
      initialDate: _dateOfBirth ?? DateTime(now.year - 30),
      firstDate: DateTime(now.year - 100),
      lastDate: DateTime(now.year - 18, now.month, now.day),
      helpText: 'Date of birth',
    );
    if (picked != null) {
      setState(() => _dateOfBirth = picked);
    }
  }

  Future<void> _submit() async {
    setState(() => _fieldErrors = <String, String>{});

    final String? dobError = Validators.dateOfBirth(_dateOfBirth);
    if (!_formKey.currentState!.validate() || dobError != null) {
      if (dobError != null) {
        setState(() => _formError = dobError);
      }
      return;
    }

    setState(() {
      _isSubmitting = true;
      _formError = null;
    });

    final Map<String, dynamic> body = <String, dynamic>{
      'firstName': _firstName.text.trim(),
      'middleName': _middleName.text.trim().isEmpty ? null : _middleName.text.trim(),
      'lastName': _lastName.text.trim(),
      'dateOfBirth': Formatters.isoDate(_dateOfBirth!),
      'gender': _gender,
      'maritalStatus': _maritalStatus,
      'idType': _idType,
      if (_nationalId.text.trim().isNotEmpty) 'nationalId': _nationalId.text.trim(),
      if (_phone.text.trim().isNotEmpty) 'phoneNumber': _phone.text.trim(),
      'email': _email.text.trim().isEmpty ? null : _email.text.trim(),
      'address': <String, dynamic>{
        'line1': _addressLine.text.trim(),
        'city': _city.text.trim(),
      },
      'occupation': _occupation.text.trim().isEmpty ? null : _occupation.text.trim(),
      'employer': _employer.text.trim().isEmpty ? null : _employer.text.trim(),
      'monthlyIncome':
          _income.text.trim().isEmpty ? null : _income.text.replaceAll(',', '').trim(),
    };

    try {
      final CustomerRepository repository = ref.read(customerRepositoryProvider);
      final Customer saved = _isEditing
          ? await repository.update(widget.customerId!, body)
          : await repository.register(body);

      ref.invalidate(customerListProvider);
      if (_isEditing) {
        ref.invalidate(customerProvider(widget.customerId!));
      }
      if (mounted) {
        showMessage(context, _isEditing ? 'Customer updated' : 'Customer registered');
        context.go(Routes.customerProfile(saved.id));
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _formError = error.fieldErrors.isEmpty ? error.message : null;
          _fieldErrors = error.fieldErrors;
        });
        // Re-run validation so the server's field errors are rendered.
        _formKey.currentState!.validate();
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return Scaffold(
        appBar: AppBar(title: const Text('Edit customer')),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(_isEditing ? 'Edit customer' : 'Register customer')),
      body: SafeArea(
        child: Responsive.constrain(
          Form(
            key: _formKey,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: <Widget>[
                if (_formError != null) ...<Widget>[
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(_formError!),
                  ),
                  const SizedBox(height: 16),
                ],
                SectionCard(
                  title: 'Personal details',
                  child: Column(
                    children: <Widget>[
                      _field(_firstName, 'First name',
                          validator: (String? v) => _validate('firstName', v,
                              () => Validators.required(v, field: 'First name'))),
                      _field(_middleName, 'Middle name (optional)'),
                      _field(_lastName, 'Last name',
                          validator: (String? v) => _validate('lastName', v,
                              () => Validators.required(v, field: 'Last name'))),
                      const SizedBox(height: 12),
                      InkWell(
                        onTap: _pickDateOfBirth,
                        child: InputDecorator(
                          decoration: const InputDecoration(
                            labelText: 'Date of birth',
                            suffixIcon: Icon(Icons.calendar_today_outlined, size: 18),
                          ),
                          child: Text(
                            _dateOfBirth == null ? 'Select' : Formatters.date(_dateOfBirth),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<String>(
                        initialValue: _gender,
                        decoration: const InputDecoration(labelText: 'Gender'),
                        items: const <DropdownMenuItem<String>>[
                          DropdownMenuItem<String>(value: 'FEMALE', child: Text('Female')),
                          DropdownMenuItem<String>(value: 'MALE', child: Text('Male')),
                          DropdownMenuItem<String>(value: 'OTHER', child: Text('Other')),
                          DropdownMenuItem<String>(
                              value: 'UNDISCLOSED', child: Text('Prefer not to say')),
                        ],
                        onChanged: (String? value) => setState(() => _gender = value),
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<String>(
                        initialValue: _maritalStatus,
                        decoration: const InputDecoration(labelText: 'Marital status'),
                        items: const <DropdownMenuItem<String>>[
                          DropdownMenuItem<String>(value: 'SINGLE', child: Text('Single')),
                          DropdownMenuItem<String>(value: 'MARRIED', child: Text('Married')),
                          DropdownMenuItem<String>(value: 'DIVORCED', child: Text('Divorced')),
                          DropdownMenuItem<String>(value: 'WIDOWED', child: Text('Widowed')),
                        ],
                        onChanged: (String? value) => setState(() => _maritalStatus = value),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SectionCard(
                  title: 'Identification',
                  child: Column(
                    children: <Widget>[
                      DropdownButtonFormField<String>(
                        initialValue: _idType,
                        decoration: const InputDecoration(labelText: 'Document type'),
                        items: const <DropdownMenuItem<String>>[
                          DropdownMenuItem<String>(
                              value: 'NATIONAL_ID', child: Text('National ID')),
                          DropdownMenuItem<String>(value: 'PASSPORT', child: Text('Passport')),
                          DropdownMenuItem<String>(
                              value: 'DRIVING_LICENCE', child: Text('Driving licence')),
                          DropdownMenuItem<String>(value: 'VOTER_ID', child: Text('Voter ID')),
                        ],
                        onChanged: (String? value) =>
                            setState(() => _idType = value ?? 'NATIONAL_ID'),
                      ),
                      const SizedBox(height: 12),
                      _field(
                        _nationalId,
                        _isEditing ? 'ID number (leave blank to keep)' : 'ID number',
                        validator: (String? v) => _validate(
                          'nationalId',
                          v,
                          () => _isEditing ? null : Validators.required(v, field: 'ID number'),
                        ),
                      ),
                      _field(
                        _phone,
                        _isEditing ? 'Phone (leave blank to keep)' : 'Phone number',
                        keyboardType: TextInputType.phone,
                        validator: (String? v) => _validate(
                          'phoneNumber',
                          v,
                          () => Validators.phone(v, isRequired: !_isEditing),
                        ),
                      ),
                      _field(
                        _email,
                        'Email (optional)',
                        keyboardType: TextInputType.emailAddress,
                        validator: (String? v) =>
                            _validate('email', v, () => Validators.email(v, isRequired: false)),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SectionCard(
                  title: 'Address and employment',
                  child: Column(
                    children: <Widget>[
                      _field(_addressLine, 'Address'),
                      _field(_city, 'City'),
                      _field(_occupation, 'Occupation'),
                      _field(_employer, 'Employer'),
                      _field(
                        _income,
                        'Monthly income',
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        inputFormatters: <TextInputFormatter>[
                          FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]')),
                        ],
                        validator: (String? v) => _validate(
                          'monthlyIncome',
                          v,
                          () => Validators.amount(v, field: 'Monthly income', isRequired: false),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                FilledButton(
                  onPressed: _isSubmitting ? null : _submit,
                  child: _isSubmitting
                      ? const SizedBox(
                          height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : Text(_isEditing ? 'Save changes' : 'Register customer'),
                ),
                const SizedBox(height: 32),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _field(
    TextEditingController controller,
    String label, {
    TextInputType? keyboardType,
    List<TextInputFormatter>? inputFormatters,
    String? Function(String?)? validator,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: controller,
        keyboardType: keyboardType,
        inputFormatters: inputFormatters,
        decoration: InputDecoration(labelText: label),
        validator: validator,
      ),
    );
  }

  /// Prefers a server-side error for this field, falling back to local rules.
  String? _validate(String field, String? value, String? Function() localRule) {
    final String? serverError = _fieldErrors[field];
    if (serverError != null) {
      return serverError;
    }
    return localRule();
  }
}
