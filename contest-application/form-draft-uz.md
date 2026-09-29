# “Eng innovatsion g‘oya” — TATU arizasi uchun ishchi nusxa

Holat: **Google Form’ga kiritilmagan va yuborilmagan**. Shaxsiy ma’lumotlar, tasdiqlar, smeta va Google Drive havolalari muallif tomonidan tekshirilishi kerak.

## 0. Ishtirok shartlari — muallif tasdiqlashi kerak

- [ ] O‘zbekiston Respublikasi fuqarosiman.
- [ ] TATU bakalavriat yoki magistratura bosqichi talabasiman.
- [ ] Tanlovga faqat bitta innovatsion g‘oya bilan qatnashaman.
- [ ] Vazirlar Mahkamasining 2026-yil 4-sentabrdagi 463-son qarori bilan tasdiqlangan Nizom va tanlov shartlarini o‘qib chiqdim.

Rasmiy Nizom: https://lex.uz/uz/docs/8452392

## 1. Muallif ma’lumotlari — to‘ldirish yoki tasdiqlash kerak

| Forma maydoni | Ishchi qiymat | Holat |
|---|---|---|
| Elektron pochta | `ruslankuvatov1708@gmail.com` | Muallif tasdiqladi; Google Form hozir boshqa akkauntda ochilgan, kiritishdan oldin akkauntni almashtirish kerak |
| F.I.Sh. (to‘liq) | `Kuvatov Ruslan Baxtiyarovich` | Muallif taqdim etgan rasmiy lotincha yozuv |
| Tug‘ilgan sana | `17.08.2005` | Talaba profilidagi ma’lumot bilan tasdiqlandi |
| Jinsi | `Erkak` | Talaba profilidagi “Мужской” qiymatidan olindi |
| Ta’lim bosqichi | `Bakalavriat` | Talaba profilidagi “Бакалавр” qiymati bilan tasdiqlandi |
| Kurs | `4-kurs` | Talaba profilidagi ma’lumot bilan tasdiqlandi |
| Fakultet va ta’lim yo‘nalishi | `Kompyuter injiniringi fakulteti, 60610500 — Kompyuter injiniringi` | Diplom topshirig‘ining 2-betidan olindi; kafedra: Kompyuter tizimlari |
| Talaba ID raqami | `380231100680` | Muallif taqdim etdi |
| Telefon raqami | `+998930089748` | Muallif taqdim etdi; forma talab qilgan formatga mos |
| Qo‘shimcha telefon | `[ixtiyoriy]` | Ixtiyoriy |
| Yashash manzili | `Toshkent shahri, Sirg‘ali tumani, Oltin Vodiy 1-tor ko‘chasi, 3-uy, 30-xonadon` | Talaba profilidagi rasmiy yozuv asosida aniqlashtirildi |
| Ilmiy rahbar F.I.Sh. | `Azamova S. F.` | Diplomda: «Азамова С. Ф.»; lotincha transliteratsiya |

## 2. Innovatsion g‘oya

### Innovatsion g‘oya nomi

**UFI Phone — LTE USB-modem uchun mahalliy mobil qo‘ng‘iroq va SMS shlyuzi**

### Yo‘nalish

**Raqamli texnologiyalar va sun’iy intellekt**

Izoh: loyiha sun’iy intellektga emas, ushbu birlashtirilgan yo‘nalishning “raqamli texnologiyalar” qismiga tegishli.

### G‘oyaning qisqacha mohiyati (300 belgigacha)

UFI Phone arzon UFI003 LTE USB-modemini Android, Linux va Windows qurilmalaridan oddiy mobil qo‘ng‘iroqlar va SMSlarni mahalliy, autentifikatsiyalangan tarmoq orqali boshqariladigan xavfsiz aloqa shlyuziga aylantiradi.

### Innovatsion g‘oyaning mazmuni (kamida 200 ta so‘z)

Arzon LTE USB-modemlar SIM-karta, mobil radio modul, SMS xotirasi va ayrim hollarda oddiy uyali qo‘ng‘iroq imkoniyatiga ega. Biroq ishlab chiqaruvchining web-interfeysi odatda faqat Internet va Wi-Fi sozlamalarini ko‘rsatadi. Natijada modem ichidagi aloqa imkoniyatlari foydalanuvchiga ochilmaydi: tashkilot alohida ish raqami uchun qo‘shimcha smartfon, qimmat GSM–VoIP shlyuzi yoki bulutli xizmat sotib olishga majbur bo‘ladi. SMSlarni tashqi messenjerga uzatish esa tasdiqlash kodlari va shaxsiy yozishmalarni uchinchi tomon bulutiga olib chiqishi mumkin.

UFI Phone tekshirilgan UFI003 modemini mahalliy aloqa shlyuziga aylantiradi. Modem ichidagi Android 4.4 tizimida ishlaydigan imtiyozli, tor vazifali xizmat SIM, qo‘ng‘iroq holati, SMS xotirasi va audio yo‘lini boshqaradi. Android planshet, Linux va Windows mijozlari modemning xususiy LAN tarmog‘iga token asosidagi HMAC-SHA-256 challenge-response protokoli orqali ulanadi. Foydalanuvchi kiruvchi va chiquvchi qo‘ng‘iroqlarni ko‘radi, javob beradi, suhbatni tugatadi, ikki tomonlama ovozdan foydalanadi, qo‘ng‘iroqlar tarixini yuritadi hamda SMSlarni suhbat ko‘rinishida o‘qib yuboradi. Asosiy ma’lumotlar ishlab chiquvchi serveriga yoki majburiy bulutga chiqmaydi.

Loyiha tashqi ko‘rinishi bir xil bo‘lgan UFI qurilmalarining ichki platformasi farq qilishini ham hisobga oladi. O‘rnatgich USB ID, Android versiyasi, baseband va boshqa belgilar bo‘yicha faqat aniq sinovdan o‘tgan profilni qabul qiladi; mos kelmaydigan modemga imtiyozli komponentlar o‘rnatilmaydi. Network Guard xizmati sovuq yuklanishdan keyin paydo bo‘ladigan LTE-only holatini aniqlab, qo‘ng‘iroq uchun xavfsiz avtomatik rejimni tiklaydi.

Maqsadli foydalanuvchilar — bitta ommaviy mobil raqamni mavjud planshet va kompyuterlarda ishlatmoqchi bo‘lgan savdo va servis nuqtalari, kampus ofislari va laboratoriyalar, shuningdek, to‘liq ATS yoki UCaaS tizimi ortiqcha bo‘lgan dala obyektlari. Hozir UFI Phone v0.5.0 ishlaydigan MVP darajasida: Ucell tarmog‘ida aniq UFI003 profilida LTE, SMS, kiruvchi va chiquvchi qo‘ng‘iroqlar, ikki tomonlama ovoz hamda qo‘ng‘iroqdan keyin LTEga qaytish sinovdan o‘tgan. Hozirgi chegaralar ochiq ko‘rsatiladi: favqulodda raqamlar bloklangan, bir vaqtning o‘zida bitta qo‘ng‘iroq va bitta audio mijoz ishlaydi, boshqa operatorlar va modem profillari alohida qabul sinovidan o‘tishi kerak.

### Innovatsion g‘oyani amalga oshirish mexanizmi

Hozirgi holat: v0.5.0 MVP, Android 8+ planshet ilovasi, Linux/Windows desktop mijozlari, UFI003 ichidagi Voice Gateway va Network Guard, bir buyruqli himoyalangan o‘rnatgich, Google Play uchun AAB va Windows/Linux yig‘malari mavjud. Ochiq repozitoriy, diplom tadqiqoti, real interfeys skrinshotlari va 2026-yil 29-sentabrda muvaffaqiyatli o‘tgan 23 ta avtomatik test mavjud. Ucell bilan aniq modem/firmware profili sinovdan o‘tgan.

Grant olingach ish 90 kunlik uch bosqichda bajariladi. 1–30-kunlarda sinov laboratoriyasi va qurilmalar matritsasi tayyorlanadi, xarid qilingan modemlarning apparat/firmware belgilari inventarizatsiya qilinadi, v0.6 relizi imzolanadi va takrorlanadigan qabul protokoli avtomatlashtiriladi. 31–60-kunlarda Mobiuz, Uzmobile, Beeline va Humans SIM/tariflari bo‘yicha SMS, kiruvchi/chiquvchi qo‘ng‘iroq, ikki tomonlama audio, LTEga qaytish va har bir konfiguratsiya uchun o‘nta sovuq yuklash tekshiriladi. 61–90-kunlarda uchta Ucell pilot nuqtasi ishga tushiriladi, o‘rnatish vaqti, uzilishlar, o‘tkazib yuborilgan hodisalar va qo‘llab-quvvatlash murojaatlari o‘lchanadi. Natijalarga ko‘ra ikkinchi modem profili qo‘shiladi, foydalanuvchi interfeysi takomillashtiriladi va macOS mijozining birinchi versiyasi tayyorlanadi. Har bir yangi operator yoki qurilma “tested / candidate / unsupported” statusi bilan hujjatlashtiriladi; isbotsiz universal moslik e’lon qilinmaydi.

### Amalga oshirishdan kutilayotgan dastlabki natijalar (kamida 100 ta so‘z)

90 kun yakunida UFI Phone v1.0 uchun imzolangan Android AAB/APK, Linux paketi va Windows installer, yangilangan manba kodi hamda o‘rnatish hujjatlari tayyor bo‘ladi. Kamida ikkita aniq modem/firmware profili xavfsiz o‘rnatgichda qo‘llab-quvvatlanadi. Ucell tayanch konfiguratsiyasi qayta tekshiriladi, Mobiuz, Uzmobile, Beeline va Humans uchun SIM va tarif bo‘yicha alohida qabul protokoli tuziladi; har bir sinovda SMS, kiruvchi va chiquvchi qo‘ng‘iroq, ikki tomonlama audio, LTEga qaytish va o‘nta sovuq yuklash natijasi qayd etiladi. Uchta pilot nuqtada tizim kamida 30 kun ishlaydi. O‘lchanadigan ko‘rsatkichlar: toza o‘rnatish vaqti, muvaffaqiyatli qo‘ng‘iroq ulushi, ikki tomonlama audio muvaffaqiyati, qayta ulanish vaqti, o‘tkazib yuborilgan hodisalar va bir pilotga to‘g‘ri keladigan qo‘llab-quvvatlash vaqti. Kamida 20 nafar sinov foydalanuvchisidan tuzilgan fikrlar asosida interfeys va qo‘llanma yangilanadi. Yakunda texnik qabul hisoboti, operatorlar matritsasi, pilot natijalari va keyingi tijorat bosqichi uchun ishonchli tannarx modeli taqdim etiladi.

## 3. Baholash mezonlari bo‘yicha dalillar

### Ilmiy va innovatsion yangilik: ilmiy/texnik asos va manbalar

UFI Phone yangi mobil aloqa standartini ixtiro qilmaydi. Ilmiy-texnik asos quyidagi amaldagi mexanizmlarning tekshiriladigan birikmasiga tayangan:

1. 3GPP TS 23.272 LTE/EPS tarmog‘idan 2G/3G circuit-switched fallback mexanizmini belgilaydi. Tekshirilgan UFI003/Ucell konfiguratsiyasida oddiy ovoz aynan shu yo‘l bilan ishlaydi: https://portal.3gpp.org/desktopmodules/Specifications/SpecificationDetails.aspx?specificationId=835
2. Android TelephonyManager telefoniya holatini kuzatish va telephony xizmatlariga kirish modelini beradi: https://developer.android.com/reference/android/telephony/TelephonyManager
3. Android Telephony provider SMS/MMS xotirasi va SMS hodisalari modelini belgilaydi: https://developer.android.com/reference/android/provider/Telephony
4. RFC 2104 HMAC orqali umumiy maxfiy kalit asosida xabar yaxlitligi va autentifikatsiyani belgilaydi: https://www.rfc-editor.org/info/rfc2104/
5. Ishlaydigan manba kodi va texnik dalillar: https://github.com/Ruskuv1708/ufi-sms-telegram-forwarder

Yangilik alohida standartlarda emas, balki arzon modemning yashirin telefoniya imkoniyatlarini xavfsiz mahsulotga aylantiruvchi tizimli yechimda: aniq apparat profilini tekshiruvchi o‘rnatgich, imtiyozli modem shlyuzi, HMAC bilan himoyalangan faqat mahalliy protokol, radio rejimini avtomatik tiklash va Android/Linux/Windows mijozlari bitta takrorlanadigan arxitekturada birlashtirilgan. Yechim isbotlangan va taxmin qilinayotgan moslikni ajratadi; tashqi ko‘rinishi bir xil, lekin ichki platformasi boshqa qurilmalarga xavfli o‘rnatishni rad etadi.

### Dolzarblik: muammo kim uchun va qanchalik keng?

O‘zbekiston Milliy statistika qo‘mitasi 2025-yil 1-aprel holatiga 36,3483 mln mobil abonent va har 100 aholiga 96,6 ta ulanishni qayd etgan: https://stat.uz/files/538/2025-Choraklik-natijalar-january--march-ang/3940/Report-for-January-March-2025.pdf

Raqobat qo‘mitasi mamlakat mobil bozorida oltita operatorni ko‘rsatadi: https://raqobat.gov.uz/ru/rezultaty-analiza-rynka-uslug-mobilnoj-svyazi/

Bu raqamlar UFI Phone mijozlari sonini avtomatik anglatmaydi, ammo SIM asosidagi aloqa infratuzilmasining kengligini ko‘rsatadi. Aniq muammo savdo va servis nuqtalari, kampus ofislari va laboratoriyalar hamda dala obyektlarida uchraydi: mavjud ishchi SIM va arzon LTE-modem Internet beradi, lekin qo‘ng‘iroq va SMS uchun alohida ekran/interfeys yo‘q. Alternativa sifatida qo‘shimcha smartfon, PBX/GSM–VoIP shlyuzi yoki bulutli xizmat kerak bo‘ladi. UFI Phone mavjud ekran va modemdan foydalanib, SMS hamda ovozni mahalliy tarmoqda saqlaydi. Muallifning UFI003/Ucell bilan o‘tkazgan apparat sinovi muammoning texnik yechilishini ko‘rsatdi. Tijorat talabi uchta pilot nuqta, kamida 20 foydalanuvchi intervyusi va o‘lchanadigan foydalanish natijalari bilan tekshiriladi; arizada hali o‘tkazilmagan so‘rov natijasi uydirilmaydi.

### Mavjud analoglar (kamida 3 ta) va farqi

1. **Dinstar UC2000-VE GSM/LTE VoIP Gateway.** Mobil tarmoq va VoIP/SIP o‘rtasida 4/8 kanalli shlyuz, SMS/USSD uchun API beradi: https://www.dinstar.com/WEB/files/15277/2018-09-06/UC2000-VE%20GSM%26LTE%20VoIP%20Gateway%20Datasheet.pdf  Farqi: alohida korporativ shlyuz va SIP/PBX infratuzilmasiga mo‘ljallangan. UFI Phone mavjud arzon UFI003 va foydalanuvchining Android/Linux/Windows ekranlaridan foydalanadi, Phone/Messages ko‘rinishidagi tayyor UI va aniq profil bo‘yicha xavfsiz o‘rnatishni beradi.
2. **Microsoft Phone Link.** Android telefonidagi SMS, bildirishnoma va qo‘ng‘iroqlarni Windows kompyuterida ko‘rsatadi; Android 10+, Windows 10/11 va bir xil Wi-Fi tarmog‘ini talab qiladi: https://support.microsoft.com/en-us/windows/apps/phonelink/phone-link-requirements-and-setup  Farqi: alohida Android smartfon zarur va mijoz Windows bilan cheklangan. UFI Phone SIM joylashgan UFI USB-modemni shlyuz qiladi hamda Android, Linux va Windows mijozlarini qo‘llaydi; Microsoft akkaunti yoki ishlab chiquvchi buluti talab qilinmaydi.
3. **Asterisk chan_dongle.** Ayrim eski Huawei UMTS USB-modemlarida Asterisk orqali qo‘ng‘iroq, SMS va USSDni qo‘llaydi: https://github.com/phcoder/asterisk-chan-dongle-1  Farqi: alpha holatidagi drayver, Asterisk/Linuxni yig‘ish va dialplan sozlashni talab qiladi, qo‘llab-quvvatlanadigan modemlar ro‘yxati boshqa. UFI Phone tekshirilgan UFI003/Android platformasida ishlaydi, bir buyruqli guided setup, oddiy foydalanuvchi ilovalari, lokal tarix, ikki tomonlama audio, radio-recovery va HMAC autentifikatsiyasini bir mahsulotda beradi.

## 4. Xarajatlar smetasi — yakuniy narxlar va havolalar tasdiqlanmaguncha kiritilmaydi

Nizomning 7-bandiga ko‘ra faqat quyidagi toifalar mos keladi:

- prototip uchun sarflash materiallari;
- uskunalar xaridi;
- xorijiy inkubatsiya, akseleratsiya yoki stajirovka xarajatlari.

Ishchi reja: modem namunalari va USB sarflash materiallari; Android planshetlar; Linux/Windows sinov noutbuklari; macOS mijozini ishlab chiqish uchun Mac mini; alohida pilot tarmoqlari uchun routerlar; audio qabul sinovi uchun USB garnituralar; quvvat adapterlari va USB hub’lar. Har bir qatorga mahsulot rasmi, dona narxi, soni, jami qiymati va faol elektron do‘kon havolasi qo‘shiladi. Yakuniy smeta alohida PDF qilinadi va muallif imzolaydi.

Forma summalari:

- Sarflash materiallari: `[___] so‘m`
- Uskunalar: `[___] so‘m`
- Xorijiy dasturlar: `0 so‘m` (agar muallif boshqacha reja tasdiqlamasa)
- Umumiy summa: `[___] so‘m`
- Narxlar manbasi va asoslash: `[yakuniy smeta tasdiqlangach ko‘chiriladi]`
- Imzolangan smeta PDF Google Drive havolasi: `[hali yo‘q]`

## 5. Kutilayotgan natijalar va holat

### O‘lchanadigan natijalar va muddatlar

- 30 kun: xarid va inventarizatsiya; takrorlanadigan test stendi; v0.6 imzolangan relizi.
- 60 kun: Ucell qayta qabul sinovi va Mobiuz/Uzmobile/Beeline/Humans bo‘yicha hujjatlashtirilgan SIM/tarif matritsasi; har bir konfiguratsiyada 10 ta sovuq yuklash.
- 90 kun: v1.0 Android/Linux/Windows relizi; kamida 2 ta exact-match modem profili; 3 ta pilot nuqta; kamida 20 sinov foydalanuvchisi; 30 kunlik pilot metrikalari; macOS mijozining birinchi versiyasi.
- Asosiy KPI: o‘rnatish vaqti, qo‘ng‘iroq va ikki tomonlama audio muvaffaqiyati, LTEga qaytish vaqti, qayta ulanish, o‘tkazib yuborilgan hodisa va bir pilotga qo‘llab-quvvatlash vaqti.

Grant uskunalari sinov matritsasi va uch pilotni bir vaqtning o‘zida ishlatish imkonini beradi; natija faqat kod emas, tekshirilgan qurilma/operator profillari va qabul hisobotlari bilan bog‘lanadi.

### G‘oyaning hozirgi rivojlanish bosqichi

**MVP mavjud**

### Demo / prototip / repozitoriy havolasi

https://github.com/Ruskuv1708/ufi-sms-telegram-forwarder

### Jamoa a’zolari va rollari

`Kuvatov Ruslan Baxtiyarovich` — yagona muallif; mahsulot arxitekturasi, modem shlyuzi, Android va desktop mijozlari, sinovlar, hujjatlar va pilotni boshqarish. Ilmiy rahbar: `Azamova S. F.` — ilmiy-uslubiy maslahat. Diplomda boshqa jamoa a’zolari ko‘rsatilmagan; forma maydonini bo‘sh qoldirishdan oldin muallif tasdiqlashi kerak.

## 6. Hujjatlar

| Talab | Lokal holat | Forma uchun yakuniy holat |
|---|---|---|
| Imzolangan ariza, PDF | Yo‘q | Shablon tayyorlash, muallif tekshiruvi, imzo, PDF, Drive’ga yuklash |
| Innovatsion g‘oya ma’lumotnomasi | Forma matnlari uchun mazmun tayyor | Agar TATU alohida fayl talab qilsa, imzolanadigan hujjat tayyorlash |
| Imzolangan xarajatlar smetasi, rasm/narx/havola bilan | Yo‘q | Xaridlar va limit tasdiqlangach tayyorlash, imzolash va Drive’ga yuklash |
| Taqdimot | Rus va ingliz PPTX mavjud | Rus deckdagi eski commit raqamini yangilash va PDF eksport qilish; Drive’ga yuklash |
| Prototip | MVP, repo va build artefaktlari mavjud | GitHub havolasi formaga tayyor |

Google Drive’da uchta talab qilinadigan obyektga **“Havolaga ega barcha foydalanuvchilar — Ko‘rish”** ruxsati berilishi kerak:

1. Imzolangan ariza PDF — `[havola]`
2. Imzolangan smeta PDF — `[havola]`
3. Taqdimot PDF yoki Google Slides — `[havola]`

## 7. Yakuniy tasdiqlar — muallif tasdiqlashi kerak

- [ ] Kiritilgan ma’lumotlar va hujjatlar haqiqiy; g‘oya mualliflik huquqlari menga tegishli va uchinchi shaxslar huquqlarini buzmaydi.
- [ ] Imzolangan rasmiy ariza shartlari bilan tanishdim.
- [ ] 2019-yil 2-iyuldagi O‘RQ-547-son Qonuniga muvofiq shaxsiy ma’lumotlarimni qayta ishlashga roziman.

## Muhim tashkiliy eslatma

TATUning rasmiy e’lonida hujjatlarni **elektron va chop etilgan shaklda** mas’ul bo‘limga topshirish talab qilingan. Manzil: C bino, 214/A-xona. Google Form yuborilishi chop etilgan nusxalarni topshirish talabini avtomatik bekor qilmaydi.
