import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { fileURLToPath, pathToFileURL } from "node:url";

const sourceDir = path.dirname(fileURLToPath(import.meta.url));
const workspaceDir = path.resolve(sourceDir, "../..");
const repoDir = workspaceDir;
const SKILL_DIR = process.env.PRESENTATIONS_SKILL_DIR
  ?? "/home/Hollow/.codex/plugins/cache/openai-primary-runtime/presentations/26.923.10815/skills/presentations";
const RUNTIME_PYTHON = process.env.RUNTIME_PYTHON
  ?? "/home/Hollow/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3";
const RUNTIME_NODE_MODULES = process.env.RUNTIME_NODE_MODULES
  ?? "/home/Hollow/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules";
const artifactToolPath = path.join(RUNTIME_NODE_MODULES, "@oai/artifact-tool/dist/artifact_tool.mjs");
const { Presentation, PresentationFile } = await import(pathToFileURL(artifactToolPath).href);
const buildDir = path.join(workspaceDir, "presentations/workbench/ufi-v050");
const outputDir = path.join(workspaceDir, "presentations/final");
const validationDir = path.join(workspaceDir, "presentations/validation");
const coverPath = path.join(workspaceDir, "presentations/assets/ufi-phone-cover.png");
const usersPath = path.join(workspaceDir, "presentations/assets/ufi-phone-users.png");
const iconPath = path.join(repoDir, "assets/ufi-phone.png");
const referencePath = path.join(workspaceDir, "presentations/reference/CellBridge_TUIT_Startup_Pitch_EN.pptx");

await fs.mkdir(buildDir, { recursive: true });
await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(validationDir, { recursive: true });

const { resolvePresentationFont, applyPresentationChartFont, finalizePresentation } = await import(
  pathToFileURL(path.join(SKILL_DIR, "container_tools/artifact_tool_utils.mjs")).href,
);

const FONT = resolvePresentationFont({ fontFamily: "Noto Sans" });
const coverBytes = await fs.readFile(coverPath);
const usersBytes = await fs.readFile(usersPath);
const iconBytes = await fs.readFile(iconPath);
const referenceBytes = await fs.readFile(referencePath);
const referenceSha256 = createHash("sha256").update(referenceBytes).digest("hex");

const C = {
  navy: "#071A2B",
  navy2: "#0D2B3E",
  ink: "#102633",
  cyan: "#23C9D7",
  cyan2: "#119AAA",
  pale: "#E8F8FA",
  white: "#F8FBFC",
  paper: "#F3F7F8",
  gray: "#637784",
  line: "#CBD9DE",
  amber: "#F4A62A",
  red: "#E05E55",
  blue: "#4B6BFB",
  green: "#36A875",
  paleGreen: "#E8F6EF",
  paleRed: "#FDEEEE",
};

const COMMIT = "116de4779f0a3eed155390c43de84b1bb24f3df5";
const GH = "https://github.com/Ruskuv1708/ufi-sms-telegram-forwarder";
const SOURCES = {
  repo: `${GH}/tree/${COMMIT}`,
  commit: `${GH}/commit/${COMMIT}`,
  actions: `${GH}/actions/runs/35964803887`,
  readme: `${GH}/blob/${COMMIT}/README.md`,
  productVision: `${GH}/blob/${COMMIT}/docs/product-vision.md`,
  operatorReport: `${GH}/blob/${COMMIT}/docs/uzbekistan-market-and-operator-compatibility.md`,
  appleRoadmap: `${GH}/blob/${COMMIT}/docs/apple-platform-roadmap.md`,
  hardwareProfiles: `${GH}/blob/${COMMIT}/hardware-profiles.json`,
  profileGuide: `${GH}/blob/${COMMIT}/docs/adding-hardware-profiles.md`,
  playChecklist: `${GH}/blob/${COMMIT}/distribution/google-play/release-checklist.md`,
  applePrice: "https://www.apple.com/shop/buy-ipad/ipad",
  ringcentral: "https://www.ringcentral.com/shared/content/plans-and-pricing.html",
  uzStats: "https://stat.uz/files/538/2025-Choraklik-natijalar-january--march-ang/3940/Report-for-January-March-2025.pdf",
  competition: "https://raqobat.gov.uz/ru/rezultaty-analiza-rynka-uslug-mobilnoj-svyazi/",
  ucell: "https://ucell.uz/en/company_news/ucell_volte_vilte-_texnologiyalarining_yangi_ufqlari",
  mobiuz: "https://corp.mobi.uz/en/uslugi/volte/?VOICE=Y",
  uzmobile: "https://uztelecom.uz/en/for-individuals/mobile-communication/gsm/services/additional-services/volte/",
  beeline: "https://b2b.beeline.uz/en/products/services/volte",
  humans: "https://qr.humans.uz/ru/telecom",
  perfectumCdma: "https://perfectum.uz/uz/cdma",
  perfectumDevices: "https://perfectum.uz/uz/pages/sotni-ustroistv",
  playTarget: "https://support.google.com/googleplay/android-developer/answer/11926878?hl=en",
  appBundle: "https://developer.android.com/guide/app-bundle",
  playSigning: "https://developer.android.com/studio/publish/app-signing",
  playClosedTest: "https://support.google.com/googleplay/android-developer/answer/14151465?hl=en",
  appleLocalNetwork: "https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy",
  callKit: "https://developer.apple.com/documentation/CallKit",
  notarization: "https://developer.apple.com/documentation/Security/notarizing-macos-software-before-distribution",
};

function addText(slide, text, x, y, w, h, options = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    position: { left: x, top: y, width: w, height: h },
    fill: options.fill ?? "none",
    line: options.line ?? { style: "solid", fill: "none", width: 0 },
    borderRadius: options.borderRadius,
  });
  shape.text = text;
  shape.text.style = {
    typeface: FONT,
    fontSize: options.fontSize ?? 24,
    bold: options.bold ?? false,
    italic: options.italic ?? false,
    color: options.color ?? C.ink,
    alignment: options.alignment ?? "left",
    verticalAlignment: options.verticalAlignment ?? "top",
    autoFit: options.autoFit ?? "shrinkText",
    wrap: options.wrap ?? "square",
    lineSpacing: options.lineSpacing ?? 1.0,
    insets: options.insets ?? { top: 0, right: 0, bottom: 0, left: 0 },
  };
  return shape;
}

function addBox(slide, x, y, w, h, fill, options = {}) {
  return slide.shapes.add({
    geometry: options.geometry ?? "roundRect",
    position: { left: x, top: y, width: w, height: h },
    fill,
    line: options.line ?? { style: "solid", fill: "none", width: 0 },
    borderRadius: options.borderRadius ?? "rounded-2xl",
    shadow: options.shadow,
  });
}

function addRule(slide, x, y, w, color = C.line, width = 1) {
  return slide.shapes.add({
    geometry: "line",
    position: { left: x, top: y, width: w, height: 0 },
    fill: "none",
    line: { style: "solid", fill: color, width },
  });
}

function addBullet(slide, text, x, y, w, options = {}) {
  addBox(slide, x, y + 8, 9, 9, options.marker ?? C.cyan, {
    geometry: "rect",
    borderRadius: 2,
  });
  return addText(slide, text, x + 20, y, w - 20, options.h ?? 54, {
    fontSize: options.fontSize ?? 23,
    color: options.color ?? C.ink,
    lineSpacing: options.lineSpacing ?? 1.04,
  });
}

function addHeader(slide, title, number, dark = false, subtitle = "") {
  const fg = dark ? C.white : C.ink;
  const muted = dark ? "#9DB4BF" : C.gray;
  addText(slide, `0${number}`, 64, 34, 44, 24, { fontSize: 15, bold: true, color: C.cyan });
  addText(slide, "UFI PHONE", 108, 34, 150, 24, { fontSize: 15, bold: true, color: muted });
  addText(slide, title, 64, 76, 1140, 72, { fontSize: 43, bold: true, color: fg, lineSpacing: 0.92 });
  if (subtitle) {
    addText(slide, subtitle, 64, 146, 1140, 38, { fontSize: 19, color: muted, lineSpacing: 1.0 });
  }
}

function addFooter(slide, number, dark = false, lang = "EN") {
  const muted = dark ? "#8CA2AD" : "#778C96";
  const footerText = lang === "RU"
    ? "Стартап-питч TUIT  •  24 сентября 2026"
    : "TUIT startup pitch  •  24 Sep 2026";
  addText(slide, footerText, 64, 684, 360, 18, { fontSize: 12, color: muted });
  addText(slide, String(number).padStart(2, "0"), 1180, 684, 36, 18, {
    fontSize: 12,
    color: muted,
    alignment: "right",
  });
}

function addNotes(slide, text) {
  slide.speakerNotes.textFrame.setText(text);
}

function addSectionLabel(slide, text, x, y, color = C.cyan) {
  addText(slide, text.toUpperCase(), x, y, 360, 24, { fontSize: 15, bold: true, color });
}

const en = {
  lang: "EN",
  coverTitle: "UFI Phone",
  coverVersion: "v0.5.0",
  coverSubtitle: "Private carrier calls and SMS on the screens you already own",
  coverLine1: "Tested UFI003 profile with Ucell",
  coverLine2: "Android, Linux and Windows release artifacts",
  coverMeta: "TUIT startup pitch  /  24 September 2026",
  s2Title: "Product proof in v0.5.0",
  s2Subtitle: "The local gateway now covers the everyday calling and messaging loop",
  s2CallsLabel: "CALLS",
  s2CallsBig: "2-way",
  s2CallsText: "Incoming and outgoing ordinary carrier calls with answer, hang up and duplex audio.",
  s2CallsNote: "Incoming-call UI and local call history are complete.",
  s2SmsLabel: "MESSAGES",
  s2SmsBig: "Local",
  s2SmsText: "Receive, read and send SMS directly over the modem's private LAN.",
  s2SmsNote: "Telegram is optional and disabled by default.",
  s2Bottom: "Network-mode health and automatic recovery keep incoming-call fallback ready after reboot.",
  s3Title: "Evidence-based product architecture",
  s3Subtitle: "The tested UFI003 owns the SIM, radio state, carrier call and SMS store",
  s3Network: "Mobile\nnetwork",
  s3Modem: "Tested UFI003\nSIM • radio • gateway",
  s3Lan: "Authenticated\nprivate LAN",
  s3Android: "Android",
  s3Linux: "Linux",
  s3Windows: "Windows",
  s3Voice: "Voice path: LTE data hands the call to WCDMA/HSPA for 2G/3G circuit-switched audio.",
  s3Ims: "Operator VoLTE availability does not give this modem a usable IMS stack.",
  s3Scope: "Scope: non-emergency ordinary calls only, one active call and one audio client.",
  s3Guard: "The guarded installer accepts the exact tested UFI003 profile. Look-alike hardware is refused.",
  s4Title: "Release packaging and platform status",
  s4Subtitle: "Commit 116de47 produced three artifacts in a successful GitHub Actions run",
  s4Headers: ["Platform", "What ships", "Built evidence", "Remaining gate"],
  s4Rows: [
    ["Android", "UFI Phone v0.5.0 with responsive Calls, Keypad and Messages UI; landscape keypad fixed", "API 36 AAB built", "Publisher account, upload signing, Play forms and closed test if applicable"],
    ["Linux", "Desktop calls, SMS, local history and two-way audio", "x86_64 executable built", "Installer and support QA on target distributions"],
    ["Windows", "Portable desktop app with calls, SMS, history and PortAudio support", "x86_64 executable built", "Installer signing and support QA"],
    ["UFI003 gateway", "Device doctor, guarded installer, calls, SMS, audio and radio recovery", "Exact profile tested", "Near-match hardware remains refused"],
  ],
  s5Title: "First-year cost benchmark for ten devices",
  s5Subtitle: "UFI Phone uses one tested gateway and screens the organization already owns",
  s5Big: "$10",
  s5BigLabel: "founder purchase price for the tested modem",
  s5Hypothesis: "Pilot pricing hypothesis",
  s5HypothesisText: "$49 starter kit + $3 per active device each month\n= $409 in year one",
  s5Point: "The pilot must measure installer time, support workload and profile-maintenance cost before launch pricing is set.",
  s5ChartCats: ["UFI Phone pilot target*", "Apple cellular premium ×10", "RingCentral Essentials ×10"],
  s5Caveat: "*Hypothesis, not a launched offer. Carrier plan, taxes and existing screens are excluded. The products do not provide the same feature set.",
  s6Title: "Uzbekistan beachhead and go-to-market",
  s6Subtitle: "Start with one tested operator and one exact device profile",
  s6Market: "36.35M",
  s6MarketLabel: "mobile subscriptions in Uzbekistan as of 1 April 2025",
  s6UseLabel: "BEACHHEAD USERS",
  s6Use1: "Retail and service desks using one public number on existing Wi-Fi screens",
  s6Use2: "Campus offices and labs that need a departmental SIM on tablets and PCs",
  s6Use3: "Field sites where a fixed PBX or a full UCaaS suite is disproportionate",
  s6ModelLabel: "GO TO MARKET",
  s6Model: "Start with paid Ucell pilots on the tested UFI003.\nAdd operators only after SIM and tariff acceptance.\nChannel hypothesis: modem resellers and local IT installers.",
  s7Title: "Uzbekistan operator compatibility",
  s7Subtitle: "VoLTE on the operator network is separate from IMS support inside this UFI003",
  s7Headers: ["Operator", "Network evidence", "UFI003 evidence", "Commercial claim"],
  s7Rows: [
    ["Ucell", "Official VoLTE offer", "Tested: LTE data, SMS, calls and two-way audio; voice falls back to WCDMA/HSPA", "Reference operator"],
    ["Mobiuz", "VoLTE page documents 2G/3G fallback", "No project SIM test yet", "Validate SIM and tariff"],
    ["Uzmobile", "VoLTE page documents 2G/3G handover", "No project SIM test yet", "Validate SIM, APN and LTE return"],
    ["Beeline", "VoLTE for compatible USIM and device", "Modem IMS unusable; circuit-switched path untested", "Validate intended tariff"],
    ["Humans", "Mobile service uses the Uzmobile network", "MVNO provisioning remains untested", "Test separately"],
    ["Perfectum", "CDMA and 5G SA/VoNR device paths", "Incompatible with this exact UFI003", "Unsupported on this hardware"],
  ],
  s8Title: "90-day commercialization plan",
  s8Subtitle: "Release packaging is ready; operator acceptance and publisher prerequisites now determine the pilot",
  s8P1: "DAYS 0–30",
  s8P1Title: "Release and measure",
  s8P1Text: "Play account, support email and upload key\nPlay App Signing and store declarations\n12 testers for 14 days if the new-account rule applies\nSigned release tag with synthetic assets",
  s8P2: "DAYS 31–60",
  s8P2Title: "Operator acceptance",
  s8P2Text: "Mobiuz, Uzmobile, Beeline and Humans\nSIM and tariff matrix\nTen cold boots per operator\nCalls, audio, SMS and return to LTE",
  s8P3: "DAYS 61–90",
  s8P3Title: "Pilot and expand",
  s8P3Text: "Three paid Ucell sites\nMeasure setup, missed events and support\nPolish Phone UX/calls; profile a second modem\nStart the macOS protocol package",
  s8Risk: "Commercial risks: 2G/3G fallback availability, operator and tariff approval, privileged gateway signing, and Play review.",
  s8AppleLabel: "APPLE ROADMAP",
  s8Apple: "macOS first. iPadOS follows for foreground calls and SMS. Reliable background ringing needs compliant push and adds a cloud dependency.",
  s8AskLabel: "TUIT ASK",
  s8Ask: "Telecom mentor\nFour operator SIMs\nThree pilot sites\nPlay tester cohort",
};

const ru = {
  lang: "RU",
  coverTitle: "UFI Phone",
  coverVersion: "v0.5.0",
  coverSubtitle: "Обычные мобильные звонки и SMS на уже имеющихся экранах",
  coverLine1: "Проверенный профиль UFI003 в сети Ucell",
  coverLine2: "Релизные сборки для Android, Linux и Windows",
  coverMeta: "Стартап-питч для TUIT  /  24 сентября 2026",
  s2Title: "Что уже работает в v0.5.0",
  s2Subtitle: "Локальный шлюз закрывает полный цикл обычных звонков и SMS",
  s2CallsLabel: "ЗВОНКИ",
  s2CallsBig: "Дуплекс",
  s2CallsText: "Входящие и исходящие вызовы, ответ, завершение и двусторонний звук.",
  s2CallsNote: "Готовы экран входящего вызова и локальная история.",
  s2SmsLabel: "СООБЩЕНИЯ",
  s2SmsBig: "Локально",
  s2SmsText: "Получение, чтение и отправка SMS напрямую по частной сети модема.",
  s2SmsNote: "Telegram не обязателен и по умолчанию отключён.",
  s2Bottom: "Контроль режима сети и автоматическое восстановление сохраняют готовность к входящим вызовам после перезагрузки.",
  s3Title: "Архитектура с доказанными границами",
  s3Subtitle: "Проверенный UFI003 управляет SIM, радиорежимом, вызовами и хранилищем SMS",
  s3Network: "Мобильная\nсеть",
  s3Modem: "Проверенный UFI003\nSIM • радио • шлюз",
  s3Lan: "Защищённая\nлокальная сеть",
  s3Android: "Android",
  s3Linux: "Linux",
  s3Windows: "Windows",
  s3Voice: "Голосовой путь: LTE передаёт вызов в WCDMA/HSPA для обычного вызова через 2G/3G.",
  s3Ims: "Наличие VoLTE у оператора не создаёт рабочий IMS-стек в этом модеме.",
  s3Scope: "Границы: только неэкстренные вызовы, один активный разговор и один аудиоклиент.",
  s3Guard: "Защищённый установщик принимает только точный профиль UFI003. Похожее оборудование отклоняется.",
  s4Title: "Релизные сборки и готовность платформ",
  s4Subtitle: "Коммит 116de47 создал три артефакта в успешном запуске GitHub Actions",
  s4Headers: ["Платформа", "Что поставляется", "Подтверждение сборки", "Оставшееся условие"],
  s4Rows: [
    ["Android", "UFI Phone v0.5.0: адаптивные Calls, Keypad и Messages; исправлена клавиатура в альбомном режиме", "Собран AAB для API 36", "Аккаунт издателя, ключ загрузки, формы Play и закрытый тест при необходимости"],
    ["Linux", "Настольные звонки, SMS, история и двусторонний звук", "Собран x86_64-файл", "Проверка установщика и поддержки на целевых дистрибутивах"],
    ["Windows", "Переносимое приложение со звонками, SMS, историей и PortAudio", "Собран x86_64-файл", "Подпись установщика и проверка поддержки"],
    ["Шлюз UFI003", "Device doctor, защищённый установщик, звонки, SMS, звук и восстановление сети", "Проверен точный профиль", "Похожие устройства пока отклоняются"],
  ],
  s5Title: "Расходы первого года для десяти устройств",
  s5Subtitle: "UFI Phone использует один проверенный шлюз и уже купленные экраны",
  s5Big: "$10",
  s5BigLabel: "цена купленного модема, а не серийная себестоимость",
  s5Hypothesis: "Гипотеза цены пилота",
  s5HypothesisText: "Стартовый комплект $49 + $3 в месяц за активное устройство\n= $409 в первый год",
  s5Point: "До запуска нужно измерить время установки, нагрузку поддержки и стоимость сопровождения профилей.",
  s5ChartCats: ["Цель UFI Phone*", "Наценка Apple ×10", "Лицензии RingCentral ×10"],
  s5Caveat: "*Гипотеза, а не действующее предложение. Не включены тариф, налоги и уже имеющиеся экраны. Наборы функций различаются.",
  s6Title: "Первый рынок в Узбекистане",
  s6Subtitle: "Выход на рынок начинается с одного проверенного оператора и точного профиля устройства",
  s6Market: "36,35 млн",
  s6MarketLabel: "мобильных подключений в Узбекистане на 1 апреля 2025 года",
  s6UseLabel: "ПЕРВЫЕ КЛИЕНТЫ",
  s6Use1: "Магазины и сервисные точки с одним публичным номером на Wi-Fi-экранах",
  s6Use2: "Офисы и лаборатории в кампусе, где SIM нужна на планшетах и ПК",
  s6Use3: "Выездные площадки, где стационарная АТС или полный UCaaS избыточны",
  s6ModelLabel: "ВЫХОД НА РЫНОК",
  s6Model: "Начать с платных пилотов Ucell на проверенном UFI003.\nДобавлять операторов только после проверки SIM и тарифа.\nГипотеза канала: продавцы модемов и локальные ИТ-интеграторы.",
  s7Title: "Совместимость с операторами Узбекистана",
  s7Subtitle: "VoLTE в сети оператора не означает поддержку IMS внутри этого UFI003",
  s7Headers: ["Оператор", "Данные о сети", "Данные по UFI003", "Коммерческое заявление"],
  s7Rows: [
    ["Ucell", "Официальная услуга VoLTE", "Проверены LTE, SMS, звонки и двусторонний звук; голос уходит в WCDMA/HSPA", "Опорный оператор"],
    ["Mobiuz", "Страница VoLTE описывает возврат в 2G/3G", "SIM проекта ещё не проверена", "Проверить SIM и тариф"],
    ["Uzmobile", "Страница VoLTE описывает переход в 2G/3G", "SIM проекта ещё не проверена", "Проверить SIM, APN и возврат в LTE"],
    ["Beeline", "VoLTE для совместимых USIM и устройств", "IMS модема не работает; обычный голосовой путь не проверен", "Проверить нужный тариф"],
    ["Humans", "Сервис работает в сети Uzmobile", "Настройки MVNO ещё не проверены", "Тестировать отдельно"],
    ["Perfectum", "CDMA и устройства 5G SA/VoNR", "Несовместимо с этим точным UFI003", "Не поддерживается этим модемом"],
  ],
  s8Title: "План коммерциализации на 90 дней",
  s8Subtitle: "Релизные сборки готовы; пилот теперь зависит от операторских тестов и условий публикации",
  s8P1: "ДНИ 0–30",
  s8P1Title: "Релиз и измерения",
  s8P1Text: "Аккаунт Play, служебная почта и ключ загрузки\nPlay App Signing и формы магазина\n12 тестировщиков на 14 дней, если правило применимо\nПодписанный релиз и синтетические материалы",
  s8P2: "ДНИ 31–60",
  s8P2Title: "Проверка операторов",
  s8P2Text: "Mobiuz, Uzmobile, Beeline и Humans\nМатрица SIM и тарифов\nДесять холодных запусков на оператора\nЗвонки, звук, SMS и возврат в LTE",
  s8P3: "ДНИ 61–90",
  s8P3Title: "Пилот и расширение",
  s8P3Text: "Три платные площадки Ucell\nМетрики установки, пропусков и поддержки\nДоработать интерфейс Phone и звонки; профиль второго модема\nСтарт общего протокола для macOS",
  s8Risk: "Коммерческие риски: доступность 2G/3G, согласование оператора и тарифа, привилегированная подпись шлюза и проверка Google Play.",
  s8AppleLabel: "ПЛАН APPLE",
  s8Apple: "Сначала macOS. Затем iPadOS для звонков и SMS на переднем плане. Надёжный фон требует корректного push-сервиса и добавляет облачную зависимость.",
  s8AskLabel: "ЗАПРОС К TUIT",
  s8Ask: "Наставник по телекоммуникациям\nSIM-карты четырёх операторов\nТри пилотные площадки\nГруппа тестировщиков Play",
};

function buildDeck(t) {
  const p = Presentation.create({ slideSize: { width: 1280, height: 720 } });
  {
    const s = p.slides.add();
    s.background.fill = C.navy;
    s.images.add({ blob: coverBytes, contentType: "image/png", alt: "Illustration of a USB cellular modem connecting a tablet, laptop and phones", fit: "cover", position: { left: 0, top: 0, width: 1280, height: 720 }, prompt: "Photorealistic deck cover: inexpensive USB cellular modem bridging a tablet, laptop and phones; dark navy studio scene; no logos." });
    addBox(s, 0, 0, 690, 720, "linear(0deg, #071A2B 0%, #071A2B/92 65%, #071A2B/20 100%)", { geometry: "rect", borderRadius: 0 });
    s.images.add({ blob: iconBytes, contentType: "image/png", alt: "UFI Phone app icon", fit: "contain", position: { left: 74, top: 48, width: 58, height: 58 } });
    addText(s, t.coverTitle, 72, 120, 600, 84, { fontSize: 64, bold: true, color: C.white, lineSpacing: 0.9 });
    addText(s, t.coverVersion, 75, 202, 300, 42, { fontSize: 27, bold: true, color: C.blue });
    addText(s, t.coverSubtitle, 74, 250, 545, 110, { fontSize: t.lang === "RU" ? 27 : 29, bold: true, color: C.cyan, lineSpacing: 1.0 });
    addRule(s, 74, 380, 86, C.cyan, 5);
    addText(s, t.coverLine1, 74, 410, 520, 40, { fontSize: 21, color: C.white });
    addText(s, t.coverLine2, 74, 454, 540, 54, { fontSize: 21, color: "#C6D8DF" });
    addText(s, t.coverMeta, 74, 642, 540, 28, { fontSize: 16, color: "#9EB5C0" });
    addText(s, t.lang, 1180, 52, 46, 26, { fontSize: 15, bold: true, color: C.white, alignment: "right" });
    addNotes(s, `${t.lang === "EN" ? "Product evidence" : "Доказательства продукта"}: UFI Phone v0.5.0 at commit ${COMMIT}. The repository documents direct local SMS, incoming and outgoing ordinary carrier calls, two-way audio, call history, incoming-call UI, radio-mode recovery, Android, Linux and Windows clients.\n${SOURCES.commit}\n${SOURCES.readme}\n\nThe cover illustration contains no customer data and does not depict the tested physical device.`);
  }
  {
    const s = p.slides.add();
    s.background.fill = C.paper;
    addHeader(s, t.s2Title, 2, false, t.s2Subtitle);
    const divider = s.shapes.add({ geometry: "line", position: { left: 640, top: 205, width: 0, height: 338 }, fill: "none", line: { style: "solid", fill: C.line, width: 1.5 } });
    divider.sendToBack();
    addSectionLabel(s, t.s2CallsLabel, 70, 208, C.cyan2);
    addText(s, t.s2CallsBig, 68, 242, 490, 108, { fontSize: t.lang === "RU" ? 66 : 82, bold: true, color: C.ink, lineSpacing: 0.88 });
    addText(s, t.s2CallsText, 72, 356, 485, 94, { fontSize: 21, color: C.ink, lineSpacing: 1.08 });
    addText(s, t.s2CallsNote, 72, 466, 485, 60, { fontSize: 18, bold: true, color: C.cyan2, lineSpacing: 1.04 });
    addSectionLabel(s, t.s2SmsLabel, 704, 208, C.blue);
    addText(s, t.s2SmsBig, 704, 242, 480, 108, { fontSize: t.lang === "RU" ? 62 : 78, bold: true, color: C.ink, lineSpacing: 0.88 });
    addText(s, t.s2SmsText, 708, 356, 480, 94, { fontSize: 21, color: C.ink, lineSpacing: 1.08 });
    addText(s, t.s2SmsNote, 708, 466, 480, 60, { fontSize: 18, bold: true, color: C.blue, lineSpacing: 1.04 });
    addBox(s, 64, 568, 1152, 78, C.navy, { borderRadius: "rounded-xl" });
    addText(s, t.s2Bottom, 92, 583, 1096, 52, { fontSize: t.lang === "RU" ? 20 : 21, bold: true, color: C.white, verticalAlignment: "middle", alignment: "center", lineSpacing: 1.0 });
    addFooter(s, 2, false, t.lang);
    addNotes(s, `${t.lang === "EN" ? "Repository evidence" : "Данные репозитория"}: direct local SMS receive/read/send, incoming and outgoing ordinary carrier calls, two-way audio, incoming-call UI, call history, network-mode health and recovery. Telegram remains an optional compatibility path and the installer leaves it disabled.\n${SOURCES.readme}\n${SOURCES.productVision}\n\nNo real phone numbers, SMS content, tokens or private screenshots are included in this deck.`);
  }
  {
    const s = p.slides.add();
    s.background.fill = C.navy;
    addHeader(s, t.s3Title, 3, true, t.s3Subtitle);
    const n1 = addBox(s, 70, 230, 190, 118, C.navy2, { line: { style: "solid", fill: "#315064", width: 1.5 }, borderRadius: "rounded-xl" });
    addText(s, t.s3Network, 88, 252, 154, 72, { fontSize: 25, bold: true, color: C.white, alignment: "center", verticalAlignment: "middle" });
    const n2 = addBox(s, 330, 214, 260, 150, C.pale, { borderRadius: "rounded-xl", shadow: "shadow-md" });
    addText(s, t.s3Modem, 350, 242, 220, 92, { fontSize: t.lang === "RU" ? 22 : 23, bold: true, color: C.ink, alignment: "center", verticalAlignment: "middle" });
    const n3 = addBox(s, 650, 214, 230, 150, C.cyan, { borderRadius: "rounded-xl", shadow: "shadow-md" });
    addText(s, t.s3Lan, 675, 242, 180, 92, { fontSize: 23, bold: true, color: C.navy, alignment: "center", verticalAlignment: "middle" });
    s.shapes.connect(n1, n2, { kind: "straight", fromSide: "right", toSide: "left", line: { style: "solid", fill: C.cyan, width: 3 }, head: { type: "triangle", width: "sm", length: "sm" }, tail: { type: "triangle", width: "sm", length: "sm" } });
    s.shapes.connect(n2, n3, { kind: "straight", fromSide: "right", toSide: "left", line: { style: "solid", fill: C.cyan, width: 3 }, head: { type: "triangle", width: "sm", length: "sm" }, tail: { type: "triangle", width: "sm", length: "sm" } });
    const d1 = addBox(s, 970, 190, 220, 78, "#173A4E", { line: { style: "solid", fill: "#3E6678", width: 1 }, borderRadius: "rounded-lg" });
    const d2 = addBox(s, 970, 286, 220, 78, "#173A4E", { line: { style: "solid", fill: "#3E6678", width: 1 }, borderRadius: "rounded-lg" });
    const d3 = addBox(s, 970, 382, 220, 78, "#173A4E", { line: { style: "solid", fill: "#3E6678", width: 1 }, borderRadius: "rounded-lg" });
    addText(s, t.s3Android, 992, 212, 176, 36, { fontSize: 22, bold: true, color: C.white, alignment: "center", verticalAlignment: "middle" });
    addText(s, t.s3Linux, 992, 308, 176, 36, { fontSize: 22, bold: true, color: C.white, alignment: "center", verticalAlignment: "middle" });
    addText(s, t.s3Windows, 992, 404, 176, 36, { fontSize: 22, bold: true, color: C.white, alignment: "center", verticalAlignment: "middle" });
    [d1, d2, d3].forEach((d) => s.shapes.connect(n3, d, { kind: "elbow", fromSide: "right", toSide: "left", line: { style: "solid", fill: "#7CDDE5", width: 2 }, head: { type: "triangle", width: "sm", length: "sm" }, tail: { type: "triangle", width: "sm", length: "sm" } }));
    addText(s, t.s3Voice, 74, 462, 520, 82, { fontSize: 20, bold: true, color: C.white, lineSpacing: 1.05 });
    addText(s, t.s3Ims, 650, 492, 540, 62, { fontSize: 19, color: "#C3D5DC", lineSpacing: 1.05 });
    addText(s, t.s3Scope, 74, 563, 520, 48, { fontSize: 16.5, bold: true, color: C.amber, lineSpacing: 1.04 });
    addText(s, t.s3Guard, 650, 568, 540, 52, { fontSize: 16.5, bold: true, color: C.amber, lineSpacing: 1.04 });
    addFooter(s, 3, true, t.lang);
    addNotes(s, `${t.lang === "EN" ? "Technical boundary" : "Техническая граница"}: the tested UFI003 firmware has no usable IMS stack. Ucell calls work by moving from LTE data to WCDMA/HSPA for circuit-switched voice, then returning to LTE. Operator VoLTE availability and modem IMS capability are separate facts.\n${SOURCES.readme}\n${SOURCES.hardwareProfiles}\n${SOURCES.operatorReport}\n\nThe gateway is restricted to the modem LAN and requires a pairing token. The current scope excludes emergency numbers, short codes, service codes, supplementary services and native IMS/VoLTE.`);
  }
  {
    const s = p.slides.add();
    s.background.fill = C.white;
    addHeader(s, t.s4Title, 4, false, t.s4Subtitle);
    const values = [t.s4Headers, ...t.s4Rows];
    const table = s.tables.add({ rows: values.length, columns: 4, left: 64, top: 198, width: 1152, height: 438, columnWidths: [160, 392, 210, 390], values });
    table.borders.assign({ style: "solid", fill: C.line, width: 1 });
    for (let r = 0; r < values.length; r++) {
      table.rows[r].height = r === 0 ? 50 : 97;
      for (let c = 0; c < 4; c++) {
        const cell = table.getCell(r, c);
        cell.fill = r === 0 ? C.navy : (r === values.length - 1 ? C.pale : (r % 2 === 0 ? "#F4F7F8" : C.white));
        cell.text.style = { typeface: FONT, fontSize: r === 0 ? 15.5 : (t.lang === "RU" ? 14.3 : 15.2), bold: r === 0 || c === 0 || c === 2, color: r === 0 ? C.white : C.ink, verticalAlignment: "middle", autoFit: "shrinkText", wrap: "square", insets: { top: 7, right: 9, bottom: 7, left: 9 }, lineSpacing: 1.0 };
      }
    }
    addFooter(s, 4, false, t.lang);
    addNotes(s, `${t.lang === "EN" ? "Build evidence" : "Подтверждение сборок"}: GitHub Actions run #3 for commit 116de47 finished successfully and produced three artifacts: Android AAB (660 KB), Linux x86_64 executable (23 MB) and Windows x86_64 executable (11.9 MB).\n${SOURCES.actions}\n${SOURCES.commit}\n\nAndroid targets API 36. The repository contains the responsive Google-style Calls, Keypad and Messages interface, including the corrected landscape keypad. Only the ordinary Android companion belongs in Google Play; the privileged modem gateway remains behind the exact-profile installer.\n${SOURCES.playChecklist}\n${SOURCES.profileGuide}`);
  }
  {
    const s = p.slides.add();
    s.background.fill = C.paper;
    addHeader(s, t.s5Title, 5, false, t.s5Subtitle);
    addText(s, t.s5Big, 70, 196, 300, 112, { fontSize: 88, bold: true, color: C.cyan2, lineSpacing: 0.88 });
    addText(s, t.s5BigLabel, 75, 309, 325, 54, { fontSize: 18, bold: true, color: C.ink, lineSpacing: 1.0 });
    addRule(s, 74, 374, 318, C.line, 1.5);
    addText(s, t.s5Hypothesis, 75, 397, 330, 36, { fontSize: 17, bold: true, color: C.blue });
    addText(s, t.s5HypothesisText, 75, 441, 350, 104, { fontSize: t.lang === "RU" ? 20 : 21, bold: true, color: C.ink, lineSpacing: 1.13 });
    addText(s, t.s5Point, 75, 562, 360, 90, { fontSize: 17, color: C.gray, lineSpacing: 1.05 });
    const chart = s.charts.add("bar", { position: { left: 470, top: 195, width: 730, height: 390 }, categories: t.s5ChartCats, series: [{ name: "USD", values: [409, 1500, 2399], valuesFormatCode: "$#,##0", fill: C.cyan, points: [{ idx: 0, fill: C.cyan2 }, { idx: 1, fill: C.amber }, { idx: 2, fill: C.blue }] }], barOptions: { direction: "bar", grouping: "clustered", gapWidth: 42 }, hasLegend: false, xAxis: { visible: false, min: 0, max: 2650, majorGridlines: null }, yAxis: { visible: true, textStyle: { typeface: FONT, fill: C.ink, fontSize: t.lang === "RU" ? 14.5 : 16, bold: true }, line: { style: "solid", fill: "none", width: 0 } }, dataLabels: { showValue: true, position: "outEnd", textStyle: { typeface: FONT, fill: C.ink, fontSize: 18, bold: true } }, chartFill: "transparent", chartLine: { style: "solid", fill: "none", width: 0 }, plotAreaFill: "transparent", plotAreaLine: { style: "solid", fill: "none", width: 0 } });
    applyPresentationChartFont(chart, { fontFamily: FONT });
    addText(s, t.s5Caveat, 470, 597, 730, 56, { fontSize: 13, color: C.gray, lineSpacing: 1.0 });
    addFooter(s, 5, false, t.lang);
    addNotes(s, `${t.lang === "EN" ? "Sources and assumptions" : "Источники и допущения"}:\n- Apple currently shows a $150 difference between Wi-Fi and Wi-Fi + Cellular at the same base iPad storage tier; 10 × $150 = $1,500. ${SOURCES.applePrice}\n- RingCentral Essentials starts at $19.99 per user each month with annual billing; 10 × $19.99 × 12 = $2,398.80, rounded to $2,399. ${SOURCES.ringcentral}\n- UFI Phone pilot pricing remains a hypothesis: $49 starter kit + $3 × 10 active devices × 12 months = $409 in year one. The $10 modem price is the founder's purchase price and is not a supplier quote or validated scalable BOM.\n\nThis compares budget anchors rather than equivalent products. RingCentral includes a broad UCaaS feature set; cellular iPad pricing buys data connectivity rather than this local carrier-call gateway.`);
  }
  {
    const s = p.slides.add();
    s.background.fill = C.navy;
    s.images.add({ blob: usersBytes, contentType: "image/png", alt: "Illustrative small business employees using a tablet, laptop and phone for a shared call", fit: "cover", crop: { left: 0.18, top: 0.02, right: 0.02, bottom: 0.02 }, position: { left: 815, top: 0, width: 465, height: 720 }, prompt: "Natural editorial photograph of a small Central Asian service business sharing calls across a tablet, laptop and phone; no logos." });
    addBox(s, 758, 0, 120, 720, "linear(0deg, #071A2B 0%, #071A2B/84 48%, #071A2B/0 100%)", { geometry: "rect", borderRadius: 0 });
    addText(s, "06", 64, 34, 44, 24, { fontSize: 15, bold: true, color: C.cyan });
    addText(s, "UFI PHONE", 108, 34, 150, 24, { fontSize: 15, bold: true, color: "#9DB4BF" });
    addText(s, t.s6Title, 64, 76, 700, 70, { fontSize: t.lang === "RU" ? 38 : 39, bold: true, color: C.white, lineSpacing: 0.94 });
    addText(s, t.s6Subtitle, 64, 150, 700, 46, { fontSize: t.lang === "RU" ? 17 : 18, color: "#9DB4BF", lineSpacing: 1.0 });
    addText(s, t.s6Market, 70, 194, 270, 76, { fontSize: t.lang === "RU" ? 50 : 58, bold: true, color: C.cyan, lineSpacing: 0.9 });
    addText(s, t.s6MarketLabel, 305, 218, 430, 42, { fontSize: 16.5, color: "#C7D9E0", lineSpacing: 1.0, verticalAlignment: "middle" });
    addRule(s, 72, 278, 640, "#294657", 1.5);
    addSectionLabel(s, t.s6UseLabel, 72, 298, C.cyan);
    addBullet(s, t.s6Use1, 72, 334, 660, { fontSize: 18.5, h: 58, color: "#C7D9E0" });
    addBullet(s, t.s6Use2, 72, 399, 660, { fontSize: 18.5, h: 58, color: "#C7D9E0" });
    addBullet(s, t.s6Use3, 72, 464, 660, { fontSize: 18.5, h: 58, color: "#C7D9E0" });
    addRule(s, 72, 530, 640, "#294657", 1.5);
    addSectionLabel(s, t.s6ModelLabel, 72, 548, C.amber);
    addText(s, t.s6Model, 72, 582, 670, 92, { fontSize: t.lang === "RU" ? 17 : 18, bold: true, color: C.white, lineSpacing: 1.1 });
    addFooter(s, 6, true, t.lang);
    addNotes(s, `${t.lang === "EN" ? "Market evidence" : "Данные рынка"}: Uzbekistan had 36.3483 million mobile subscriptions, or 96.6 per 100 residents, as of 1 April 2025. The national competition authority identifies six operators. These figures show market scale and do not forecast UFI Phone demand.\n${SOURCES.uzStats}\n${SOURCES.competition}\n${SOURCES.operatorReport}\n\nCustomer segments, channel design and paid-pilot sequencing remain hypotheses for validation. The image is generated and illustrative; it contains no real customer, call or message data.`);
  }
  {
    const s = p.slides.add();
    s.background.fill = C.white;
    addHeader(s, t.s7Title, 7, false, t.s7Subtitle);
    const values = [t.s7Headers, ...t.s7Rows];
    const table = s.tables.add({ rows: values.length, columns: 4, left: 64, top: 192, width: 1152, height: 456, columnWidths: [140, 300, 452, 260], values });
    table.borders.assign({ style: "solid", fill: C.line, width: 1 });
    for (let r = 0; r < values.length; r++) {
      table.rows[r].height = r === 0 ? 48 : 68;
      for (let c = 0; c < 4; c++) {
        const cell = table.getCell(r, c);
        const rowFill = r === 1 ? C.paleGreen : (r === values.length - 1 ? C.paleRed : (r % 2 === 0 ? "#F4F7F8" : C.white));
        cell.fill = r === 0 ? C.navy : rowFill;
        cell.text.style = { typeface: FONT, fontSize: r === 0 ? 15 : (t.lang === "RU" ? 13.7 : 14.4), bold: r === 0 || c === 0 || c === 3, color: r === 0 ? C.white : C.ink, verticalAlignment: "middle", autoFit: "shrinkText", wrap: "square", insets: { top: 5, right: 8, bottom: 5, left: 8 }, lineSpacing: 1.0 };
      }
    }
    addFooter(s, 7, false, t.lang);
    addNotes(s, `${t.lang === "EN" ? "Operator evidence" : "Источники по операторам"}:\n- Ucell VoLTE/ViLTE announcement: ${SOURCES.ucell}\n- Mobiuz VoLTE and 2G/3G fallback: ${SOURCES.mobiuz}\n- Uzmobile VoLTE and 2G/3G handover: ${SOURCES.uzmobile}\n- Beeline VoLTE requirements: ${SOURCES.beeline}\n- Humans network information: ${SOURCES.humans}\n- Perfectum CDMA and supported-device ecosystem: ${SOURCES.perfectumCdma} and ${SOURCES.perfectumDevices}\n- Project compatibility report and required SIM acceptance matrix: ${SOURCES.operatorReport}\n\nOnly Ucell has passed the project's LTE data, direct SMS, incoming/outgoing call and two-way-audio tests on this exact UFI003. Mobiuz, Uzmobile, Beeline and Humans require actual SIM and tariff validation. Perfectum paths are unsupported on this hardware.`);
  }
  {
    const s = p.slides.add();
    s.background.fill = C.navy;
    addHeader(s, t.s8Title, 8, true, t.s8Subtitle);
    const phases = [{ x: 66, label: t.s8P1, title: t.s8P1Title, body: t.s8P1Text, accent: C.cyan }, { x: 450, label: t.s8P2, title: t.s8P2Title, body: t.s8P2Text, accent: C.blue }, { x: 834, label: t.s8P3, title: t.s8P3Title, body: t.s8P3Text, accent: C.amber }];
    for (const ph of phases) {
      addText(s, ph.label, ph.x, 202, 300, 24, { fontSize: 15, bold: true, color: ph.accent });
      addText(s, ph.title, ph.x, 238, 330, 58, { fontSize: t.lang === "RU" ? 21 : 22, bold: true, color: C.white, lineSpacing: 0.94 });
      addRule(s, ph.x, 306, 320, ph.accent, 4);
      addText(s, ph.body, ph.x, 326, 330, 148, { fontSize: t.lang === "RU" ? 15.2 : 16, color: "#C7D9E0", lineSpacing: 1.1 });
    }
    addBox(s, 64, 486, 1152, 66, "#311F1D", { line: { style: "solid", fill: "#7B4D46", width: 1.3 }, borderRadius: "rounded-xl" });
    addText(s, t.s8Risk, 88, 501, 1104, 38, { fontSize: t.lang === "RU" ? 17 : 18, bold: true, color: "#FFD5CE", alignment: "center", verticalAlignment: "middle" });
    addSectionLabel(s, t.s8AppleLabel, 70, 582, C.cyan);
    addText(s, t.s8Apple, 70, 610, 630, 58, { fontSize: t.lang === "RU" ? 15.2 : 16.2, color: C.white, lineSpacing: 1.04 });
    addSectionLabel(s, t.s8AskLabel, 735, 582, C.amber);
    addText(s, t.s8Ask, 880, 578, 300, 92, { fontSize: t.lang === "RU" ? 15.5 : 16.5, bold: true, color: C.white, lineSpacing: 1.05 });
    addFooter(s, 8, true, t.lang);
    addNotes(s, `${t.lang === "EN" ? "Publisher and platform sources" : "Источники по публикации и платформам"}:\n- Google Play checklist and current project prerequisites: ${SOURCES.playChecklist}\n- Target API requirements: ${SOURCES.playTarget}\n- Android App Bundles: ${SOURCES.appBundle}\n- Play App Signing: ${SOURCES.playSigning}\n- Production access for new personal accounts: ${SOURCES.playClosedTest}\n- Apple platform roadmap: ${SOURCES.appleRoadmap}\n- Apple local-network privacy: ${SOURCES.appleLocalNetwork}\n- CallKit: ${SOURCES.callKit}\n- macOS notarization: ${SOURCES.notarization}\n\nmacOS is the recommended first Apple platform. An iPadOS foreground companion can use the local modem connection, but reliable background incoming calls require a compliant push architecture. That choice adds a server and changes the current local-only privacy model. Milestones and pilot counts are proposed targets, not completed outcomes.`);
  }
  return p;
}

async function renderDraftPreviews(presentation, prefix) {
  const dir = path.join(buildDir, `${prefix}-previews`);
  await fs.mkdir(dir, { recursive: true });
  for (let i = 0; i < presentation.slides.items.length; i++) {
    const slide = presentation.slides.items[i];
    const preview = await presentation.export({ slide, format: "png", scale: 1 });
    await fs.writeFile(path.join(dir, `slide-${String(i + 1).padStart(2, "0")}.png`), new Uint8Array(await preview.arrayBuffer()));
  }
}

async function finalizeDeck(t, outName) {
  const presentation = buildDeck(t);
  await renderDraftPreviews(presentation, t.lang.toLowerCase());
  const candidatePath = path.join(buildDir, `${t.lang.toLowerCase()}-candidate.pptx`);
  await (await PresentationFile.exportPptx(presentation, { materializeLiteralChartWorkbooks: true, nativeChartTargetApplication: "powerpoint" })).save(candidatePath);
  const finalPath = path.join(outputDir, outName);
  const stagedOutputDir = path.join(buildDir, "validated");
  await fs.mkdir(stagedOutputDir, { recursive: true });
  const stagedFinalPath = path.join(stagedOutputDir, outName);
  const receiptPath = path.join(buildDir, `${outName}.validation.json`);
  await fs.rm(stagedFinalPath, { force: true });
  await fs.rm(receiptPath, { force: true });
  const tableOwners = [4, 7];
  const result = await finalizePresentation({
    explicitTotalSlideCount: 8,
    requiredNativeTableOwnerSlides: tableOwners,
    requiredNativeChartOwnerSlides: [5],
    materializeLiteralChartWorkbooks: true,
    nativeChartTargetApplication: "powerpoint",
    workspaceDir,
    candidatePath,
    finalPath: stagedFinalPath,
    pythonExecutable: RUNTIME_PYTHON,
    integrityValidatorPath: path.join(SKILL_DIR, "container_tools/inspect_presentation_package_integrity.py"),
    layoutValidatorPath: path.join(SKILL_DIR, "container_tools/inspect_presentation_layout_geometry.py"),
    layoutArgs: ["--expected-slide-size-emu", "12192000,6858000", "--validate-bullet-geometry", "--validate-heading-fit", ...tableOwners.flatMap((number) => ["--require-native-table-slide", String(number)])],
    fontPolicy: { basis: "reference", families: [FONT], referencePath, referenceSha256 },
    verifyArtifactToolImport: true,
    receiptPath,
  });
  await fs.copyFile(stagedFinalPath, finalPath);
  await fs.copyFile(receiptPath, path.join(validationDir, `${outName}.validation.json`));
  return { finalPath, result };
}

const results = [];
results.push(await finalizeDeck(en, "UFI_Phone_v0.5.0_TUIT_Startup_Pitch_EN_FINAL.pptx"));
results.push(await finalizeDeck(ru, "UFI_Phone_v0.5.0_TUIT_Startup_Pitch_RU_FINAL.pptx"));
console.log(JSON.stringify(results, null, 2));
