# The Most Innovative Idea — working draft for the TATU application

Status: **The Google Form has not been fully completed or submitted.** The applicant must verify the personal information, declarations, budget, and Google Drive links.

Language note: this English version is a reference translation. The official form is in Uzbek, and neither the regulation nor the TATU announcement explicitly confirms that English submissions are accepted. Use the Uzbek version for the actual submission unless the TATU coordinator gives written approval to submit in English.

## 0. Participation conditions — applicant confirmation required

- [ ] I am a citizen of the Republic of Uzbekistan.
- [ ] I am a TATU bachelor's or master's student.
- [ ] I am participating in the competition with only one innovative idea.
- [ ] I have read the Regulation and competition conditions approved by Cabinet of Ministers Resolution No. 463 of 4 September 2026.

Official Regulation: https://lex.uz/uz/docs/8452392

## 1. Applicant information — complete or confirm

| Form field | Working value | Status |
|---|---|---|
| Email address | `ruslankuvatov1708@gmail.com` | Confirmed by the applicant; the value displayed in the form matches |
| Full name | `Kuvatov Ruslan Baxtiyarovich` | Official Latin spelling provided by the applicant |
| Date of birth | `17 August 2005` | Confirmed by the student profile |
| Gender | `Male` | Taken from the “Мужской” value in the student profile |
| Degree level | `Bachelor's degree` | Confirmed by the “Бакалавр” value in the student profile |
| Year of study | `4th year` | Confirmed by the student profile |
| Faculty and study programme | `Faculty of Computer Engineering, 60610500 — Computer Engineering` | Taken from page 2 of the diploma assignment; department: Computer Systems |
| Student ID | `380231100680` | Provided by the applicant |
| Phone number | `+998930089748` | Provided by the applicant; matches the format required by the form |
| Additional phone number | `[optional]` | Optional |
| Residential address | `3 Oltin Vodiy 1-tor Street, Apartment 30, Sirgali District, Tashkent, Uzbekistan` | Clarified using the official student profile |
| Academic supervisor | `Azamova S. F.` | Transliteration of “Азамова С. Ф.” from the diploma |

## 2. Innovative idea

### Name of the innovative idea

**UFI Phone — a local mobile call and SMS gateway for an LTE USB modem**

### Category

**Digital technologies and artificial intelligence**

Note: the project belongs to the “digital technologies” part of this combined category; it is not presented as an artificial-intelligence project.

### Short description of the idea (maximum 300 characters)

UFI Phone turns an inexpensive UFI003 LTE USB modem into a secure communications gateway for ordinary mobile calls and SMS, controlled from Android, Linux, and Windows devices over a local authenticated network.

### Description of the innovative idea (minimum 200 words)

Inexpensive LTE USB modems contain a SIM card, mobile radio module, SMS storage and, in some cases, ordinary cellular calling capabilities. However, the manufacturer's web interface normally exposes only Internet and Wi-Fi settings. The communications functions inside the modem are therefore unavailable to the user: an organisation may have to purchase an additional smartphone, an expensive GSM–VoIP gateway, or a cloud service for a separate business number. Forwarding SMS messages to an external messenger may also send verification codes and private correspondence to a third-party cloud.

UFI Phone converts a tested UFI003 modem into a local communications gateway. A privileged, narrowly scoped service running on Android 4.4 inside the modem controls the SIM, call state, SMS storage, and audio path. Android tablet, Linux, and Windows clients connect to the modem's private LAN using a token-based HMAC-SHA-256 challenge-response protocol. The user can see incoming and outgoing calls, answer or end a call, use two-way audio, maintain call history, and read and send SMS messages in a conversation-style interface. Core data does not need to leave the local network for a developer-operated server or mandatory cloud service.

The project also accounts for the fact that visually identical UFI devices may contain different internal platforms. The installer accepts only an exact tested profile based on USB ID, Android version, baseband, and other characteristics; privileged components are not installed on a non-matching modem. The Network Guard service detects the LTE-only state that can appear after a cold boot and restores a safe automatic radio mode required for ordinary calls.

Target users include retail and service points, campus offices and laboratories, and remote field locations that want to use one public mobile number through existing tablets and computers, but do not need a full PBX or UCaaS platform. UFI Phone v0.5.0 is currently a working MVP. LTE data, SMS, incoming and outgoing calls, two-way audio, and return to LTE after a call have been tested with Ucell on one exact UFI003 profile. Current limits are stated openly: emergency numbers are blocked, only one call and one audio client can operate at a time, and other operators and modem profiles require separate acceptance testing.

### Implementation mechanism

Current state: a v0.5.0 MVP exists, including an Android 8+ tablet application, Linux and Windows desktop clients, Voice Gateway and Network Guard services inside the UFI003, a protected one-command installer, a Google Play AAB, and Windows/Linux builds. The project includes a public repository, diploma research, real interface screenshots, and 23 automated tests that passed on 29 September 2026. One exact modem/firmware profile has been tested with Ucell.

After receiving the grant, the work will be completed in three stages over 90 days. During days 1–30, a test laboratory and device matrix will be prepared, the hardware and firmware characteristics of purchased modems will be inventoried, release v0.6 will be signed, and the repeatable acceptance protocol will be automated. During days 31–60, SMS, incoming and outgoing calls, two-way audio, return to LTE, and ten cold boots per configuration will be tested using SIM cards and tariffs from Mobiuz, Uzmobile, Beeline, and Humans. During days 61–90, three Ucell pilot sites will be launched, and installation time, disconnects, missed events, and support requests will be measured. Based on the results, a second modem profile will be added, the user interface will be improved, and the first macOS client will be prepared. Every new operator or device will be documented with a “tested”, “candidate”, or “unsupported” status; unsupported universal compatibility will not be claimed.

### Expected initial results (minimum 100 words)

By the end of 90 days, UFI Phone v1.0 will include signed Android AAB/APK packages, a Linux package, a Windows installer, updated source code, and installation documentation. The protected installer will support at least two exact modem/firmware profiles. The Ucell reference configuration will be retested, and separate SIM- and tariff-specific acceptance records will be prepared for Mobiuz, Uzmobile, Beeline, and Humans. Each test will record SMS, incoming and outgoing calls, two-way audio, return to LTE, and the results of ten cold boots. The system will operate at three pilot sites for at least 30 days. Measured indicators will include clean installation time, successful call rate, two-way audio success, reconnection time, missed events, and support time per pilot. The interface and instructions will be updated using structured feedback from at least 20 test users. The final deliverables will include a technical acceptance report, an operator matrix, pilot results, and a reliable cost model for the next commercial stage.

## 3. Evidence against the assessment criteria

### Scientific and innovative novelty: technical basis and sources

UFI Phone does not claim to invent a new mobile communications standard. Its scientific and technical basis is a verifiable combination of the following existing mechanisms:

1. 3GPP TS 23.272 defines circuit-switched fallback from LTE/EPS to 2G/3G. Ordinary voice calls use this path in the tested UFI003/Ucell configuration: https://portal.3gpp.org/desktopmodules/Specifications/SpecificationDetails.aspx?specificationId=835
2. Android TelephonyManager provides the model for monitoring telephony state and accessing telephony services: https://developer.android.com/reference/android/telephony/TelephonyManager
3. The Android Telephony provider defines SMS/MMS storage and SMS event models: https://developer.android.com/reference/android/provider/Telephony
4. RFC 2104 defines HMAC for message integrity and authentication using a shared secret: https://www.rfc-editor.org/info/rfc2104/
5. Working source code and technical evidence: https://github.com/Ruskuv1708/ufi-sms-telegram-forwarder

The novelty is not in the individual standards but in a system that turns the hidden telephony capabilities of an inexpensive modem into a secure product. An installer that verifies the exact hardware profile, a privileged modem gateway, a local-only HMAC-protected protocol, automatic radio-mode recovery, and Android/Linux/Windows clients are combined in one repeatable architecture. The system separates demonstrated compatibility from assumed compatibility and rejects unsafe installation on devices that look identical but contain a different internal platform.

### Relevance: who experiences the problem and how widespread is it?

As of 1 April 2025, the National Statistics Committee of Uzbekistan reported 36.3483 million mobile subscribers and 96.6 subscriptions per 100 people: https://stat.uz/files/538/2025-Choraklik-natijalar-january--march-ang/3940/Report-for-January-March-2025.pdf

The Competition Committee identifies six operators in the country's mobile market: https://raqobat.gov.uz/ru/rezultaty-analiza-rynka-uslug-mobilnoj-svyazi/

These figures do not automatically represent the number of UFI Phone customers, but they demonstrate the scale of SIM-based communications infrastructure. The specific problem occurs in retail and service locations, campus offices and laboratories, and remote field sites: an existing work SIM and inexpensive LTE modem provide Internet access but no dedicated interface for calls and SMS. Alternatives require an additional smartphone, a PBX/GSM–VoIP gateway, or a cloud service. UFI Phone uses the existing screen and modem while keeping SMS and voice on the local network. The author's hardware testing with UFI003 and Ucell demonstrates technical feasibility. Commercial demand will be validated through three pilot sites, at least 20 user interviews, and measurable usage results; the application does not invent survey results that have not yet been collected.

### Existing alternatives (at least three) and differences

1. **Dinstar UC2000-VE GSM/LTE VoIP Gateway.** A four- or eight-channel gateway between mobile networks and VoIP/SIP, with an API for SMS/USSD: https://www.dinstar.com/WEB/files/15277/2018-09-06/UC2000-VE%20GSM%26LTE%20VoIP%20Gateway%20Datasheet.pdf  Difference: it is a separate enterprise gateway designed for SIP/PBX infrastructure. UFI Phone uses an existing inexpensive UFI003 and the user's Android/Linux/Windows screens, provides a ready-to-use Phone/Messages interface, and enforces safe installation for an exact tested profile.
2. **Microsoft Phone Link.** Displays SMS, notifications, and calls from an Android phone on a Windows computer; it requires Android 10+, Windows 10/11, and the same Wi-Fi network: https://support.microsoft.com/en-us/windows/apps/phonelink/phone-link-requirements-and-setup  Difference: it requires a separate Android smartphone and restricts the client to Windows. UFI Phone makes the UFI USB modem containing the SIM into the gateway and supports Android, Linux, and Windows clients without requiring a Microsoft account or a developer-operated cloud.
3. **Asterisk chan_dongle.** Supports calls, SMS, and USSD through Asterisk using certain older Huawei UMTS USB modems: https://github.com/phcoder/asterisk-chan-dongle-1  Difference: it is an alpha-stage driver that requires compiling and configuring Asterisk/Linux and a dial plan, and it supports a different modem set. UFI Phone operates on the tested UFI003/Android platform and combines guided one-command setup, user-facing applications, local history, two-way audio, radio recovery, and HMAC authentication in one product.

## 4. Cost estimate — do not enter until final prices and links are approved

Under clause 7 of the Regulation, only the following categories are eligible:

- consumable materials required for the prototype;
- equipment purchases;
- foreign incubation, acceleration, or internship expenses.

Working plan: modem samples and USB consumables; Android tablets; Linux/Windows test laptops; a Mac mini for macOS client development; routers for separate pilot networks; USB headsets for audio acceptance tests; power adapters; and USB hubs. Each line must include a product image, unit price, quantity, total cost, and an active online-store link. The final estimate will be prepared as a separate PDF and signed by the applicant.

Form amounts:

- Consumable materials: `[___] UZS`
- Equipment: `[___] UZS`
- Foreign programmes: `0 UZS` unless the applicant approves a different plan
- Total: `[___] UZS`
- Price sources and justification: `[to be copied after the final estimate is approved]`
- Google Drive link to the signed budget PDF: `[not available yet]`

## 5. Expected results and current status

### Measurable results and deadlines

- Day 30: purchasing and inventory; repeatable test bench; signed v0.6 release.
- Day 60: Ucell re-acceptance testing and a documented SIM/tariff matrix for Mobiuz, Uzmobile, Beeline, and Humans; ten cold boots for every configuration.
- Day 90: v1.0 Android/Linux/Windows release; at least two exact-match modem profiles; three pilot sites; at least 20 test users; 30 days of pilot metrics; first macOS client version.
- Main KPIs: installation time, successful calls and two-way audio, return-to-LTE time, reconnection, missed events, and support time per pilot.

Grant-funded equipment will make it possible to operate the test matrix and three pilot sites simultaneously. The result will not be code alone; it will include verified device/operator profiles and acceptance reports.

### Current development stage

**MVP available**

### Demo, prototype, or repository link

https://github.com/Ruskuv1708/ufi-sms-telegram-forwarder

### Team members and roles

`Kuvatov Ruslan Baxtiyarovich` — sole author; responsible for product architecture, the modem gateway, Android and desktop clients, testing, documentation, and pilot management. Academic supervisor: `Azamova S. F.` — academic and methodological advice. No other team members are listed in the diploma; the applicant must confirm this before leaving the form field blank.

## 6. Documents

| Requirement | Current local status | Required final status for the form |
|---|---|---|
| Signed application, PDF | Not available | Prepare template, applicant review, signature, PDF, and Google Drive upload |
| Innovative idea information sheet | Content is ready for the form fields | If TATU requires a separate file, prepare a signable document |
| Signed cost estimate with images, prices, and links | Not available | Prepare after purchases and the budget ceiling are approved, sign, and upload to Google Drive |
| Presentation | Russian and English PPTX files are available | Update the old commit number in the Russian deck, export to PDF, and upload to Google Drive |
| Prototype | MVP, repository, and build artifacts are available | GitHub link is ready for the form |

The following three required Google Drive items must have **“Anyone with the link — Viewer”** access:

1. Signed application PDF — `[link]`
2. Signed budget PDF — `[link]`
3. Presentation PDF or Google Slides — `[link]`

## 7. Final declarations — applicant confirmation required

- [ ] The information and documents provided are authentic; I own the intellectual property rights to the idea, and it does not infringe the rights of third parties.
- [ ] I have read the conditions contained in the signed official application.
- [ ] I consent to the processing of my personal data under Law No. O‘RQ-547 of 2 July 2019.

## Important submission note

The official TATU announcement requires the documents to be submitted to the responsible university department in **both electronic and printed form**. Location: Building C, Room 214/A. Submitting the Google Form does not automatically replace the printed-document requirement.
