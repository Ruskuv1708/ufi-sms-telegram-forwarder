# Uzbekistan market and operator compatibility

Research checked: September 24, 2026. “Operator supports VoLTE” and “this
UFI003 can make a VoLTE call” are different claims. The tested firmware has no
usable IMS stack, so its working voice path is circuit-switched 2G/3G fallback.

## Market snapshot

Uzbekistan is a large mobile-first market. The National Statistics Committee
reported 36.3483 million mobile subscriptions, or 96.6 per 100 residents, as
of April 1, 2025. Its 2025 indicator series reports 81.9 individual cellular
subscriptions per 100 residents, while 99.3% of people aged 10+ used a mobile
phone. These are scale indicators, not a forecast of demand for this product.

The national competition authority identifies six operators: Uztelecom
(Uzmobile), Unitel (Beeline), Coscom (Ucell), Universal Mobile Systems
(Mobiuz), Humans, and Rubicon Wireless Communication (Perfectum). Government
updates report more than eight million Mobiuz subscriptions in July 2025 and
7.7 million Beeline subscriptions as of September 2025. Do not infer complete
market shares from those two counts alone.

Primary sources:

- [National Statistics Committee, January–March 2025 report](https://stat.uz/files/538/2025-Choraklik-natijalar-january--march-ang/3940/Report-for-January-March-2025.pdf)
- [Competition Committee mobile-market analysis](https://raqobat.gov.uz/ru/rezultaty-analiza-rynka-uslug-mobilnoj-svyazi/)
- [Government update on Mobiuz](https://gov.uz/en/digital/news/view/69710)
- [Government update on Beeline](https://gov.uz/en/digital/news/view/123905)

## Operator matrix for the tested UFI003

| Operator | Operator network evidence | UFI003 status | Product decision |
|---|---|---|---|
| Ucell | Officially offers VoLTE/ViLTE in LTE coverage with compatible 4G SIM/device. | **Tested:** LTE data, SMS, incoming/outgoing calls and two-way audio work; calls fall back to WCDMA/HSPA. | Supported reference operator. Continue regression tests after firmware or network changes. |
| Mobiuz | Official VoLTE page says calls can fall back to 2G/3G outside LTE. | **High-priority validation:** architecture is compatible in principle; no project SIM test yet. | Run full SIM matrix before claiming support. |
| Uzmobile / Uztelecom | Official VoLTE page documents LTE coverage and 2G/3G handover outside LTE. | **High-priority validation:** expected to fit the circuit-switched fallback model; not yet project-tested. | Run full SIM matrix and APN/data-return checks. |
| Humans | Humans states that its mobile service uses the UzMobile network. | **Unverified MVNO:** underlying radio network is promising, but SIM/tariff provisioning can differ. | Test separately; do not inherit the Uzmobile result automatically. |
| Beeline Uzbekistan | Officially offers nationwide VoLTE in LTE coverage for supported devices/USIM and notes plan/service restrictions. | **Unverified:** data/SMS are plausible; this modem cannot use Beeline IMS without matching certified firmware. Circuit-switched call behavior needs a SIM test. | Test consumer and any intended M2M tariff separately. |
| Perfectum | Official materials describe the legacy CDMA network and a newer 5G SA/VoNR device ecosystem. | **Unsupported on this hardware:** the generic GSM/UMTS/LTE UFI003 is neither a Perfectum CDMA modem nor a supported 5G SA/VoNR router. | Exclude from UFI003 claims; consider separate hardware research only. |

Operator sources:

- [Ucell VoLTE/ViLTE announcement](https://ucell.uz/en/company_news/ucell_volte_vilte-_texnologiyalarining_yangi_ufqlari)
- [Beeline VoLTE requirements](https://b2b.beeline.uz/en/products/services/volte)
- [Mobiuz VoLTE](https://corp.mobi.uz/en/uslugi/volte/?VOICE=Y)
- [Uztelecom / Uzmobile VoLTE](https://uztelecom.uz/en/for-individuals/mobile-communication/gsm/services/additional-services/volte/)
- [Humans telecom network information](https://qr.humans.uz/ru/telecom)
- [Perfectum CDMA](https://perfectum.uz/uz/cdma)
- [Perfectum supported devices](https://perfectum.uz/uz/pages/sotni-ustroistv)

## Required SIM acceptance test

For each operator and intended tariff, record:

1. SIM registration and operator name after cold boot.
2. LTE data and modem Wi-Fi routing.
3. Incoming and outgoing SMS, multipart SMS, and non-Latin text.
4. Incoming call while idle on LTE: ring, answer, two-way audio, hang up.
5. Outgoing call: dialing, ringback, answer, two-way audio, hang up.
6. Radio transition during a call and return to LTE data afterward.
7. Ten cold boots: confirm automatic mode 9 and no persistent busy-line state.
8. Concurrent tablet/desktop client behavior and one-audio-client enforcement.
9. Carrier balance/charging and any plan restriction.

Publish a carrier as supported only after this matrix passes on a named modem
firmware and SIM/tariff. Market the product as a “local carrier call and SMS
gateway with fallback voice,” not as a universal VoLTE modem.
