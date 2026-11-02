---
id: UC10
name: Create Walk-in Patient Profile
feature: Patient & Family Profile Management
primary_actors: [Receptionist]
secondary_actors: []
br: [BR-03, BR-14, BR-15]
msg: [MSG-04, MSG-17, MSG-18, MSG-54, MSG-90]
screens: [Patient Search, Walk-in Patient Profile, Patient Details]
source: Report 3 revision (7), section 2.3.1; receptionist search revision approved by the user on 2026-10-27
---

# UC10 - Create Walk-in Patient Profile

## Accepted revision — 2026-10-27

This revision separates receptionist record discovery from identity confirmation. It replaces the phone-plus-birthdate prerequisite for searching or opening the creation form in the earlier UC10 and Patient Search screen description. Reference [BR-14] using this revised receptionist flow. The historical appendix and original Word report are not edited by this change. Patient portal record access and care-link verification are unchanged.

## Preconditions

The Receptionist is signed in and authorized to access administrative patient information within their work scope. A patient presents for care.

## Normal Flow

1. The Receptionist opens Patient Search or selects Create Profile directly.
2. When searching, the Receptionist enters a name, phone number, or patient code. Date of birth is an optional additional filter.
3. The system returns matching administrative candidates with patient code, full name, date of birth and contact information [BR-03]. It does not automatically select a candidate.
4. The Receptionist compares the identifying information with the person at the counter [BR-14]. If an existing profile is confirmed, they use that profile rather than creating a duplicate.
5. If a new profile is required, the Receptionist opens or continues the creation form and enters full name, date of birth, sex, phone number and address. Identification number/passport and email are optional.
6. The Receptionist confirms that identity and existing records have been checked. Opening this form does not require a successful prior search.
7. Before saving, the system validates the administrative information and checks for duplicate normalized full name + phone + date of birth, or duplicate identification number [BR-14].
8. The system creates the patient profile without creating a login account [BR-15], displays [MSG-90], and shows the saved record for subsequent use.

## Alternative Flows

- **A1. Multiple candidates.** Matching names or shared phone numbers return multiple candidates. The Receptionist narrows the list using the optional birthdate or compares the identifying information before selecting a profile. Multiple search results alone are not an error.
- **A2. Direct creation.** The Receptionist opens Create Profile before searching. Steps 5–8 still require identity confirmation, validation and duplicate detection.
- **A3. Search normalization.** Names match without regard to Vietnamese accents or letter case; name words may be separated by other name words. Phone queries support partial matching and normalization of `+84` and separators. Codes support case-insensitive partial matching with or without spaces/hyphens. An optional birthdate must match exactly.
- **A4. No results.** The system explains that no matching profile was found; the Receptionist may refine the search or create a profile. No new profile is created automatically.

## Exceptions

- **E1. Invalid search.** A keyword that is empty or contains no letters/digits, or an invalid optional birthdate, produces [MSG-54]. An empty search does not list the entire patient store.
- **E2. Invalid profile or unconfirmed identity.** The system shows field feedback or [MSG-17] and does not save.
- **E3. Duplicate profile.** The system displays [MSG-18], preserves the entered information, and does not create another record. The Receptionist may return to search to use the existing profile.
- **E4. Service failure.** The system displays [MSG-04] and does not report a successful save.

## Postconditions

An existing identified profile is selected, or a validated new administrative profile exists. No login account or clinical-history record is created.

## Screen and integration notes

- The Patient Search screen uses one keyword field and an optional birthdate field, a result list and an always-available Create Profile action (except while submitting).
- Results show only administrative information for matching; medical history and allergies remain outside Receptionist access.
- Check-in and other consequential actions retain their own identity/presence confirmations. Search matching alone is not identity verification or authorization.
- The frontend currently uses the synthetic receptionist adapter. The production lookup contract is in `docs/fe-be-api-contract-patient-reception.md`, section 3.4 and R00. Backend authorization, filtering, pagination and duplicate checks must be enforced by the server when integrated.
