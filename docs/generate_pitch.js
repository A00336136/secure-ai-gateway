// SecureAI Gateway Enterprise Pitch Deck Generator
// Uses pptxgenjs - strict no-# hex colors, no reused option objects

const pptxgen = require("/opt/homebrew/lib/node_modules/pptxgenjs");

const OUT_FILE = "/Users/ashaik/Music/secure-ai-gateway/docs/SecureAI_Gateway_Enterprise_Pitch.pptx";

// ── Color palette ──────────────────────────────────────────────────────────
const C = {
  bg:        "1A1A1A",
  card:      "2A2A2A",
  card2:     "252525",
  darkRed:   "8B0000",
  red:       "CC0000",
  white:     "FFFFFF",
  gray:      "CCCCCC",
  gray2:     "999999",
  green:     "2E7D32",
  greenDark: "1B5E20",
  headerRow: "CC0000",
  rowA:      "252525",
  rowB:      "2A2A2A",
};

const FONT = "Calibri";

async function build() {
  const pres = new pptxgen();
  pres.layout = "LAYOUT_WIDE"; // 13.3" × 7.5"
  pres.author = "Altaf Shaik";
  pres.title  = "SecureAI Gateway – Enterprise Pitch Deck";

  const W = 13.3;  // slide width
  const H = 7.5;   // slide height

  // ── Helper: slide background ──────────────────────────────────────────────
  function darkBg(slide) {
    slide.background = { color: C.bg };
  }

  // ── Helper: standard section heading ─────────────────────────────────────
  function heading(slide, text, opts = {}) {
    slide.addText(text, {
      x: opts.x ?? 0.35, y: opts.y ?? 0.25,
      w: opts.w ?? W - 0.7, h: opts.h ?? 0.7,
      fontSize: opts.fontSize ?? 30,
      fontFace: FONT, bold: true,
      color: C.white, align: "left", valign: "middle",
      margin: 0,
    });
  }

  // ── Helper: red accent line under heading ─────────────────────────────────
  function redLine(slide, y = 0.97, xStart = 0.35, lineW = W - 0.7) {
    slide.addShape(pres.shapes.RECTANGLE, {
      x: xStart, y, w: lineW, h: 0.04,
      fill: { color: C.red }, line: { color: C.red, width: 0 },
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 1 – TITLE
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    // Vertical centering: title block spans middle of slide
    // Title
    s.addText("SecureAI Gateway", {
      x: 0.5, y: 1.4, w: W - 1, h: 1.3,
      fontSize: 60, fontFace: FONT, bold: true,
      color: C.white, align: "center", valign: "middle", margin: 0,
    });

    // Red bar under title
    s.addShape(pres.shapes.RECTANGLE, {
      x: 1.5, y: 2.78, w: W - 3, h: 0.07,
      fill: { color: C.red }, line: { color: C.red, width: 0 },
    });

    // Subtitle
    s.addText("Enterprise-Grade LLM Security Framework", {
      x: 0.5, y: 2.9, w: W - 1, h: 0.65,
      fontSize: 28, fontFace: FONT, bold: false,
      color: C.white, align: "center", valign: "middle", margin: 0,
    });

    // Tagline
    s.addText("Zero Cloud Dependencies  \u2022  87\u201393% Cost Savings  \u2022  100% On-Premise", {
      x: 0.5, y: 3.65, w: W - 1, h: 0.55,
      fontSize: 18, fontFace: FONT,
      color: C.gray, align: "center", valign: "middle", margin: 0,
    });

    // Bottom version line
    s.addText("v2.0  |  IEEE Conference Paper  |  Open Source", {
      x: 0.5, y: 6.7, w: W - 1, h: 0.4,
      fontSize: 14, fontFace: FONT,
      color: C.gray2, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 2 – THE PROBLEM
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "Enterprise LLMs Are Deployed Without Adequate Security", { fontSize: 28 });
    redLine(s);

    // 4 cards side by side
    const cards = [
      { title: "Single Point of Failure",  body: "Most deployments rely on one content filter. One bypass = full exposure." },
      { title: "Data Sovereignty Risk",    body: "Commercial guardrails (AWS, Azure, OpenAI) send your prompts to the cloud. GDPR violation." },
      { title: "Regulatory Non-Compliance",body: "GDPR Article 25, EU AI Act 2024/1689, NIST AI 600-1 requirements ignored." },
      { title: "Hidden Costs",             body: "Per-request pricing at $15\u201330 per 1,000 evaluations. $1.5\u20132M over 3 years for large enterprises." },
    ];

    const cardW  = 3.0;
    const cardH  = 4.2;
    const startX = 0.25;
    const gap    = 0.22;
    const cardY  = 1.1;

    cards.forEach((c, i) => {
      const cx = startX + i * (cardW + gap);
      // Card background
      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: cardY, w: cardW, h: cardH,
        fill: { color: C.card }, line: { color: C.card, width: 0 },
      });
      // Red left border
      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: cardY, w: 0.07, h: cardH,
        fill: { color: C.red }, line: { color: C.red, width: 0 },
      });
      // Card title
      s.addText(c.title, {
        x: cx + 0.15, y: cardY + 0.2, w: cardW - 0.25, h: 0.65,
        fontSize: 17, fontFace: FONT, bold: true,
        color: C.white, align: "left", valign: "middle", margin: 0,
      });
      // Card body
      s.addText(c.body, {
        x: cx + 0.15, y: cardY + 0.95, w: cardW - 0.25, h: cardH - 1.1,
        fontSize: 15, fontFace: FONT,
        color: C.gray, align: "left", valign: "top", margin: 0,
      });
    });

    // Bottom stat bar
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: H - 0.72, w: W, h: 0.62,
      fill: { color: C.red }, line: { color: C.red, width: 0 },
    });
    s.addText("94% of enterprises have no formal LLM security policy (Gartner, 2024)", {
      x: 0.3, y: H - 0.72, w: W - 0.6, h: 0.62,
      fontSize: 14, fontFace: FONT, bold: true,
      color: C.white, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 3 – SOLUTION
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "SecureAI Gateway: Defence-in-Depth for Enterprise LLMs", { fontSize: 26 });
    redLine(s);

    // Tagline box
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0.35, y: 1.08, w: W - 0.7, h: 0.52,
      fill: { color: C.darkRed }, line: { color: C.darkRed, width: 0 },
    });
    s.addText("The ONLY open-source, on-premise LLM gateway with parallel 3-layer guardrails", {
      x: 0.45, y: 1.08, w: W - 0.9, h: 0.52,
      fontSize: 14, fontFace: FONT, bold: true,
      color: C.white, align: "center", valign: "middle", margin: 0,
    });

    // 3 columns
    const cols = [
      {
        header: "Layer 1: Policy",
        bullets: ["NVIDIA NeMo Guardrails", "Colang 2.0 DSL", "50+ jailbreak patterns", "System prompt protection"],
      },
      {
        header: "Layer 2: Content Safety",
        bullets: ["Meta LlamaGuard 3", "MLCommons AI Safety Taxonomy", "12 harm categories (S1\u2013S12)", "Semantic understanding"],
      },
      {
        header: "Layer 3: PII Protection",
        bullets: ["Microsoft Presidio", "50+ entity types", "16 languages", "GDPR Article 25 compliant"],
      },
    ];

    const colW  = 3.8;
    const colH  = 3.8;
    const colY  = 1.72;
    const gapC  = 0.42;
    const startCX = 0.35;

    cols.forEach((c, i) => {
      const cx = startCX + i * (colW + gapC);
      // Column background
      s.addShape(pres.shapes.RECTANGLE, {
        x: cx, y: colY, w: colW, h: colH,
        fill: { color: C.card }, line: { color: C.card, width: 0 },
      });
      // Red circle accent
      s.addShape(pres.shapes.OVAL, {
        x: cx + colW / 2 - 0.35, y: colY + 0.18, w: 0.7, h: 0.7,
        fill: { color: C.red }, line: { color: C.red, width: 0 },
      });
      // Layer number in circle
      s.addText(String(i + 1), {
        x: cx + colW / 2 - 0.35, y: colY + 0.18, w: 0.7, h: 0.7,
        fontSize: 18, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      // Header
      s.addText(c.header, {
        x: cx + 0.12, y: colY + 1.05, w: colW - 0.24, h: 0.52,
        fontSize: 15, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      // Bullets
      const bulletItems = c.bullets.map((b, bi) => ({
        text: b,
        options: { bullet: true, breakLine: bi < c.bullets.length - 1 },
      }));
      s.addText(bulletItems, {
        x: cx + 0.2, y: colY + 1.65, w: colW - 0.4, h: colH - 1.85,
        fontSize: 12.5, fontFace: FONT,
        color: C.gray, align: "left", valign: "top",
      });
    });

    // Bottom footnote
    s.addText("All 3 layers execute in PARALLEL via Project Reactor \u2014 44% faster than sequential execution", {
      x: 0.35, y: H - 0.65, w: W - 0.7, h: 0.48,
      fontSize: 13, fontFace: FONT, italic: true,
      color: C.gray2, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 4 – ARCHITECTURE
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "System Architecture", {});
    redLine(s);

    // ── Left panel: flow diagram ──────────────────────────────────────────
    const lw = 7.6;
    const flowItems = [
      { label: "User Request",        highlight: false },
      { label: "JWT Auth",            highlight: false },
      { label: "Rate Limiter",        highlight: false },
      { label: "NeMo | LlamaGuard | Presidio", highlight: true  },
      { label: "Decision Engine",     highlight: false },
      { label: "Ollama LLM",          highlight: false },
      { label: "PII Redaction",       highlight: false },
      { label: "Groundedness Check",  highlight: false },
      { label: "Safe Response",       highlight: false },
    ];

    const boxW  = 2.9;
    const boxH  = 0.42;
    const boxX  = 0.35;
    const boxStartY = 1.15;
    const boxGap    = 0.58;

    flowItems.forEach((item, i) => {
      const by = boxStartY + i * boxGap;
      const bg = item.highlight ? C.red : C.card;
      s.addShape(pres.shapes.RECTANGLE, {
        x: boxX, y: by, w: boxW, h: boxH,
        fill: { color: bg }, line: { color: item.highlight ? C.red : "444444", width: 1 },
      });
      s.addText(item.label, {
        x: boxX, y: by, w: boxW, h: boxH,
        fontSize: 11.5, fontFace: FONT, bold: item.highlight,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      // Arrow down (except last)
      if (i < flowItems.length - 1) {
        s.addShape(pres.shapes.RECTANGLE, {
          x: boxX + boxW / 2 - 0.02, y: by + boxH, w: 0.04, h: boxGap - boxH,
          fill: { color: "555555" }, line: { color: "555555", width: 0 },
        });
      }
    });

    // ── Right panel: stat boxes ───────────────────────────────────────────
    const stats = [
      { val: "~90ms",     label: "guardrail latency" },
      { val: "5.6%",      label: "of total request time" },
      { val: "1.6s",      label: "end-to-end P50" },
      { val: "100 req/hr",label: "rate limiting" },
      { val: "Zero",      label: "cloud API calls" },
    ];

    const rx   = lw + 0.45;
    const rw   = W - rx - 0.35;
    const sbH  = 0.88;
    const sbStartY = 1.15;
    const sbGap    = 1.06;

    stats.forEach((st, i) => {
      const sy = sbStartY + i * sbGap;
      s.addShape(pres.shapes.RECTANGLE, {
        x: rx, y: sy, w: rw, h: sbH,
        fill: { color: C.card }, line: { color: C.red, width: 1.5 },
      });
      s.addText(st.val, {
        x: rx, y: sy + 0.04, w: rw, h: 0.5,
        fontSize: 22, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      s.addText(st.label, {
        x: rx, y: sy + 0.5, w: rw, h: 0.36,
        fontSize: 11, fontFace: FONT,
        color: C.gray, align: "center", valign: "middle", margin: 0,
      });
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 5 – BENCHMARKS
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "Validated Performance Results", {});
    redLine(s);

    const metrics = [
      { val: "100%", label: "Jailbreak Interception Rate", sub: "14 attack patterns tested" },
      { val: "0",    label: "False Negatives on PII",      sub: "Credit cards, SSNs, IBANs" },
      { val: "90ms", label: "Guardrail Latency P50",       sub: "44% faster than sequential" },
      { val: "93%",  label: "Max Cost Savings",            sub: "vs commercial alternatives" },
    ];

    const mw = (W - 0.6 - 0.35) / 2;
    const mh = 2.8;
    const startMX = 0.3;
    const startMY = 1.1;
    const gapMX = mw + 0.35;
    const gapMY = mh + 0.28;

    metrics.forEach((m, i) => {
      const col = i % 2;
      const row = Math.floor(i / 2);
      const mx = startMX + col * gapMX;
      const my = startMY + row * gapMY;
      s.addShape(pres.shapes.RECTANGLE, {
        x: mx, y: my, w: mw, h: mh,
        fill: { color: C.greenDark }, line: { color: C.green, width: 1.5 },
      });
      s.addText(m.val, {
        x: mx, y: my + 0.15, w: mw, h: 1.3,
        fontSize: 72, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      s.addText(m.label, {
        x: mx + 0.1, y: my + 1.52, w: mw - 0.2, h: 0.68,
        fontSize: 17, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      s.addText(m.sub, {
        x: mx + 0.1, y: my + 2.24, w: mw - 0.2, h: 0.44,
        fontSize: 13.5, fontFace: FONT,
        color: C.gray, align: "center", valign: "middle", margin: 0,
      });
    });

    // Bottom evaluation bar
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: H - 0.68, w: W, h: 0.6,
      fill: { color: "111111" }, line: { color: "111111", width: 0 },
    });
    s.addText("Evaluated against OWASP LLM Top 10  |  MLCommons AILuminate  |  HarmBench  |  MITRE ATLAS", {
      x: 0.3, y: H - 0.68, w: W - 0.6, h: 0.6,
      fontSize: 12.5, fontFace: FONT, bold: false,
      color: C.gray2, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 6 – COMPLIANCE
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "Enterprise Compliance Coverage", {});
    redLine(s);

    const frameworks = [
      { name: "GDPR Article 25",     desc: "Privacy by Design & Default — data protection built-in" },
      { name: "EU AI Act 2024/1689", desc: "High-risk AI system requirements & transparency obligations" },
      { name: "OWASP LLM Top 10",   desc: "Prompt injection, insecure output, training data poisoning" },
      { name: "NIST AI 600-1",      desc: "Generative AI risk management framework — LLM-as-Judge" },
      { name: "NIST AI RMF",        desc: "Govern, Map, Measure, Manage — full lifecycle AI risk" },
      { name: "MITRE ATLAS",        desc: "Adversarial threat landscape for AI-enabled systems" },
      { name: "SOC 2 Type II",      desc: "Security, availability, confidentiality trust principles" },
      { name: "CIS Controls v8",    desc: "18 critical security controls — enterprise hardening" },
      { name: "ISO 27001",          desc: "Information security management system certification" },
    ];

    const cols = 3;
    const rows = 3;
    const fw   = (W - 0.5 - 0.3) / cols;
    const fh   = 1.72;
    const fStartX = 0.25;
    const fStartY = 1.1;
    const fGapX = fw + 0.15;
    const fGapY = fh + 0.12;

    frameworks.forEach((f, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      const fx = fStartX + col * fGapX;
      const fy = fStartY + row * fGapY;

      s.addShape(pres.shapes.RECTANGLE, {
        x: fx, y: fy, w: fw, h: fh,
        fill: { color: C.card2 }, line: { color: C.card2, width: 0 },
      });
      // Red top accent line
      s.addShape(pres.shapes.RECTANGLE, {
        x: fx, y: fy, w: fw, h: 0.07,
        fill: { color: C.red }, line: { color: C.red, width: 0 },
      });
      s.addText(f.name, {
        x: fx + 0.1, y: fy + 0.12, w: fw - 0.2, h: 0.6,
        fontSize: 15, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      s.addText(f.desc, {
        x: fx + 0.1, y: fy + 0.76, w: fw - 0.2, h: fh - 0.9,
        fontSize: 12, fontFace: FONT,
        color: C.gray, align: "center", valign: "top", margin: 0,
      });
    });

    // Bottom red banner
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: H - 0.62, w: W, h: 0.54,
      fill: { color: C.red }, line: { color: C.red, width: 0 },
    });
    s.addText("No other open-source LLM gateway covers this compliance breadth", {
      x: 0.3, y: H - 0.62, w: W - 0.6, h: 0.54,
      fontSize: 13.5, fontFace: FONT, bold: true,
      color: C.white, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 7 – COST ANALYSIS
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "3-Year Total Cost of Ownership Analysis", {});
    redLine(s);

    // Table
    const tableData = [
      [
        { text: "Scale",             options: { bold: true, color: C.white, fill: { color: C.red }, fontSize: 13, fontFace: FONT } },
        { text: "Users",             options: { bold: true, color: C.white, fill: { color: C.red }, fontSize: 13, fontFace: FONT } },
        { text: "SecureAI Gateway",  options: { bold: true, color: C.white, fill: { color: C.red }, fontSize: 13, fontFace: FONT } },
        { text: "Commercial Cloud",  options: { bold: true, color: C.white, fill: { color: C.red }, fontSize: 13, fontFace: FONT } },
        { text: "Savings",           options: { bold: true, color: C.white, fill: { color: C.red }, fontSize: 13, fontFace: FONT } },
      ],
      [
        { text: "Small Enterprise",  options: { color: C.white, fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "10\u201350",        options: { color: C.gray,  fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "~$10K",             options: { color: C.gray,  fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "$72\u2013180K",     options: { color: C.gray,  fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "87%",               options: { color: C.green, fill: { color: C.rowA }, fontSize: 13, fontFace: FONT, bold: true } },
      ],
      [
        { text: "Medium Enterprise", options: { color: C.white, fill: { color: C.rowB }, fontSize: 12.5, fontFace: FONT } },
        { text: "100\u2013500",      options: { color: C.gray,  fill: { color: C.rowB }, fontSize: 12.5, fontFace: FONT } },
        { text: "~$38K",             options: { color: C.gray,  fill: { color: C.rowB }, fontSize: 12.5, fontFace: FONT } },
        { text: "$378\u2013630K",    options: { color: C.gray,  fill: { color: C.rowB }, fontSize: 12.5, fontFace: FONT } },
        { text: "90%",               options: { color: C.green, fill: { color: C.rowB }, fontSize: 13, fontFace: FONT, bold: true } },
      ],
      [
        { text: "Large Enterprise",  options: { color: C.white, fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "1000+",             options: { color: C.gray,  fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "~$140K",            options: { color: C.gray,  fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "$1.5\u20132.0M",    options: { color: C.gray,  fill: { color: C.rowA }, fontSize: 12.5, fontFace: FONT } },
        { text: "93%",               options: { color: C.green, fill: { color: C.rowA }, fontSize: 13, fontFace: FONT, bold: true } },
      ],
    ];

    s.addTable(tableData, {
      x: 0.35, y: 1.15, w: 8.9,
      rowH: 0.82,
      border: { pt: 1, color: "333333" },
      colW: [2.5, 1.4, 2.0, 2.0, 1.0],
      align: "center",
    });

    // Right callout box
    const rx = 9.3;
    const ry = 1.15;
    const rw = W - rx - 0.25;
    const rh = 5.0;

    s.addShape(pres.shapes.RECTANGLE, {
      x: rx, y: ry, w: rw, h: rh,
      fill: { color: C.card }, line: { color: C.red, width: 2 },
    });
    s.addText("Why so much cheaper?", {
      x: rx + 0.12, y: ry + 0.18, w: rw - 0.24, h: 0.5,
      fontSize: 13.5, fontFace: FONT, bold: true,
      color: C.white, align: "left", valign: "middle", margin: 0,
    });
    const whyBullets = [
      "Zero licensing fees",
      "No per-request pricing",
      "No cloud GPU rental",
      "No vendor lock-in",
      "Built-in GDPR compliance",
    ];
    const bulletItems = whyBullets.map((b, bi) => ({
      text: b,
      options: { bullet: true, breakLine: bi < whyBullets.length - 1 },
    }));
    s.addText(bulletItems, {
      x: rx + 0.12, y: ry + 0.78, w: rw - 0.24, h: rh - 1.0,
      fontSize: 12, fontFace: FONT,
      color: C.gray, align: "left", valign: "top",
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 8 – TECH STACK
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "Enterprise-Grade Technology Stack", {});
    redLine(s);

    const colW2 = (W - 0.4 - 0.3) / 2;
    const colH2 = 5.0;
    const col1X = 0.2;
    const col2X = col1X + colW2 + 0.3;
    const colY2 = 1.05;

    const panels = [
      {
        x: col1X, label: "Application Layer",
        items: [
          "Spring Boot 3.4.3 / Java 21",
          "Project Reactor (WebFlux)",
          "JJWT 0.12.6 + BCrypt",
          "Bucket4j + Redis",
          "Resilience4j (Circuit Breaker)",
          "OpenAPI 3 / Swagger",
        ],
      },
      {
        x: col2X, label: "AI & Security Layer",
        items: [
          "Ollama (LLaMA 3.1 8B)",
          "NVIDIA NeMo 0.10.0",
          "Meta LlamaGuard 3",
          "Microsoft Presidio v2.2",
          "LLM-as-Judge (NIST AI 600-1)",
          "OWASP LLM10 Compliance",
        ],
      },
    ];

    panels.forEach((p) => {
      // Background
      s.addShape(pres.shapes.RECTANGLE, {
        x: p.x, y: colY2, w: colW2, h: colH2,
        fill: { color: C.card }, line: { color: C.card, width: 0 },
      });
      // Red header bar
      s.addShape(pres.shapes.RECTANGLE, {
        x: p.x, y: colY2, w: colW2, h: 0.52,
        fill: { color: C.red }, line: { color: C.red, width: 0 },
      });
      s.addText(p.label, {
        x: p.x + 0.1, y: colY2, w: colW2 - 0.2, h: 0.6,
        fontSize: 18, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      const bulletItems = p.items.map((it, bi) => ({
        text: it,
        options: { bullet: true, breakLine: bi < p.items.length - 1, fontSize: 16 },
      }));
      s.addText(bulletItems, {
        x: p.x + 0.22, y: colY2 + 0.7, w: colW2 - 0.44, h: colH2 - 0.88,
        fontSize: 16, fontFace: FONT,
        color: C.gray, align: "left", valign: "top",
      });
    });

    // DevSecOps strip at bottom
    const stripY = colY2 + colH2 + 0.12;
    const stripH = H - stripY;
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: stripY, w: W, h: stripH,
      fill: { color: "111111" }, line: { color: "111111", width: 0 },
    });
    s.addText("DevSecOps:  Jenkins CI/CD (15 stages)  |  JaCoCo 80%  |  SpotBugs  |  SonarQube  |  OWASP Dep-Check  |  Trivy  |  Garak  |  Promptfoo  |  Kubernetes + HPA", {
      x: 0.3, y: stripY, w: W - 0.6, h: stripH,
      fontSize: 13, fontFace: FONT,
      color: C.gray2, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 9 – DEVSECOPS PIPELINE
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "15-Stage DevSecOps Pipeline", {});
    redLine(s);

    // Pipeline stages
    const stages = [
      "1.Setup",
      "2.Maven Build",
      "3.Unit Tests + JaCoCo",
      "4.SpotBugs",
      "5.SonarQube",
      "6.Archive",
      "7.Docker Build",
      "7b.AI Red-Team",
      "8.Trivy",
      "9.Docker Push",
      "10.Deploy",
      "11.Karate E2E",
      "12.Cleanup",
    ];

    // Two rows of pipeline boxes
    const row1 = stages.slice(0, 7);
    const row2 = stages.slice(7);

    const bw  = 1.65;
    const bh  = 0.65;
    const gap = 0.1;
    const row1Y = 1.15;
    const row2Y = row1Y + bh + 0.55;
    const totalRow1W = row1.length * bw + (row1.length - 1) * gap;
    const row1StartX = (W - totalRow1W) / 2;
    const totalRow2W = row2.length * bw + (row2.length - 1) * gap;
    const row2StartX = (W - totalRow2W) / 2;

    function drawPipelineRow(rowItems, rowY, startX) {
      rowItems.forEach((stage, i) => {
        const bx = startX + i * (bw + gap);
        const isHighlight = stage === "7b.AI Red-Team" || stage === "5.SonarQube" || stage === "8.Trivy";
        s.addShape(pres.shapes.RECTANGLE, {
          x: bx, y: rowY, w: bw, h: bh,
          fill: { color: isHighlight ? C.darkRed : C.card },
          line: { color: isHighlight ? C.red : "444444", width: 1 },
        });
        s.addText(stage, {
          x: bx, y: rowY, w: bw, h: bh,
          fontSize: 10, fontFace: FONT, bold: isHighlight,
          color: C.white, align: "center", valign: "middle", margin: 0,
        });
        // Arrow
        if (i < rowItems.length - 1) {
          const ax = bx + bw;
          s.addShape(pres.shapes.RECTANGLE, {
            x: ax, y: rowY + bh / 2 - 0.03, w: gap, h: 0.06,
            fill: { color: "555555" }, line: { color: "555555", width: 0 },
          });
        }
      });
    }

    drawPipelineRow(row1, row1Y, row1StartX);
    drawPipelineRow(row2, row2Y, row2StartX);

    // Red highlight box
    const hbY = row2Y + bh + 0.4;
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0.35, y: hbY, w: W - 0.7, h: 0.58,
      fill: { color: C.darkRed }, line: { color: C.red, width: 1 },
    });
    s.addText("7 Build-Blocking Security Gates", {
      x: 0.5, y: hbY, w: W - 1, h: 0.58,
      fontSize: 15, fontFace: FONT, bold: true,
      color: C.white, align: "center", valign: "middle", margin: 0,
    });

    // Stats row
    s.addText("206+ test methods  |  29 test classes  |  80% line coverage  |  70% branch coverage  |  Zero CVEs CVSS \u2265 7.0", {
      x: 0.35, y: hbY + 0.72, w: W - 0.7, h: 0.48,
      fontSize: 13, fontFace: FONT,
      color: C.gray, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 10 – VALUE PROPOSITION
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "Business Value Proposition", {});
    redLine(s);

    const vpCards = [
      {
        header: "Immediate ROI",
        body:   "Deploy in hours, not months. No cloud contracts. No DPAs. No vendor negotiations. Savings start on day 1.",
      },
      {
        header: "Regulatory Shield",
        body:   "GDPR, EU AI Act, NIST, OWASP, SOC 2 ready out of the box. Compliance documentation included.",
      },
      {
        header: "Future-Proof Architecture",
        body:   "Open source, no vendor lock-in. Extensible Colang DSL. Kubernetes-native with HPA auto-scaling.",
      },
    ];

    const vcW  = (W - 0.7 - 0.4) / 3;
    const vcH  = 4.2;
    const vcY  = 1.15;
    const vcGap = 0.2;

    vpCards.forEach((vc, i) => {
      const vx = 0.35 + i * (vcW + vcGap);
      s.addShape(pres.shapes.RECTANGLE, {
        x: vx, y: vcY, w: vcW, h: vcH,
        fill: { color: C.card2 }, line: { color: C.card2, width: 0 },
      });
      // Red top border
      s.addShape(pres.shapes.RECTANGLE, {
        x: vx, y: vcY, w: vcW, h: 0.07,
        fill: { color: C.red }, line: { color: C.red, width: 0 },
      });
      s.addText(vc.header, {
        x: vx + 0.15, y: vcY + 0.2, w: vcW - 0.3, h: 0.62,
        fontSize: 17, fontFace: FONT, bold: true,
        color: C.white, align: "center", valign: "middle", margin: 0,
      });
      s.addText(vc.body, {
        x: vx + 0.15, y: vcY + 0.95, w: vcW - 0.3, h: vcH - 1.15,
        fontSize: 13, fontFace: FONT,
        color: C.gray, align: "left", valign: "top", margin: 0,
      });
    });

    // Bottom footnote
    s.addText("Trusted by: IEEE peer-reviewed research  |  Built on: NeMo + LlamaGuard + Presidio (used by FAANG)", {
      x: 0.35, y: vcY + vcH + 0.25, w: W - 0.7, h: 0.48,
      fontSize: 12.5, fontFace: FONT, italic: true,
      color: C.gray2, align: "center", valign: "middle", margin: 0,
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SLIDE 11 – ABOUT THE BUILDER
  // ══════════════════════════════════════════════════════════════════════════
  {
    const s = pres.addSlide();
    darkBg(s);

    heading(s, "Built By", { fontSize: 22 });
    redLine(s, 0.9);

    // Name
    s.addText("Altaf Shaik", {
      x: 0.35, y: 1.05, w: W - 0.7, h: 1.1,
      fontSize: 48, fontFace: FONT, bold: true,
      color: C.white, align: "left", valign: "middle", margin: 0,
    });

    // Title / role
    s.addText("Full Stack Security Engineer  |  AI/ML Platform Architect", {
      x: 0.35, y: 2.1, w: W - 0.7, h: 0.52,
      fontSize: 20, fontFace: FONT,
      color: C.red, align: "left", valign: "middle", margin: 0,
    });

    // 5 highlight bullets
    const highlights = [
      "IEEE Conference Paper: SecureAI Gateway: A Three-Layer Defence-in-Depth Framework",
      "Designed and implemented all 4 modules, 15-stage CI/CD, Kubernetes deployment",
      "Expertise: Spring Boot, Java 21, LLM Security, DevSecOps, Cloud Architecture",
      "Solved: GDPR compliance + LLM security + Cost optimization in a single framework",
      "GitHub: github.com/A00336136/secure-ai-gateway",
    ];

    const bulletItems2 = highlights.map((h, bi) => ({
      text: h,
      options: { bullet: true, breakLine: bi < highlights.length - 1 },
    }));
    s.addText(bulletItems2, {
      x: 0.35, y: 2.75, w: W - 0.7, h: 2.9,
      fontSize: 15, fontFace: FONT,
      color: C.white, align: "left", valign: "top",
    });

    // Bottom contact strip
    const csY = H - 0.78;
    s.addShape(pres.shapes.RECTANGLE, {
      x: 0, y: csY, w: W, h: 0.7,
      fill: { color: C.card2 }, line: { color: C.card2, width: 0 },
    });
    s.addText("LinkedIn: linkedin.com/in/altaf-shaik   |   GitHub: github.com/A00336136   |   Email: altafshaik.dev@gmail.com", {
      x: 0.3, y: csY, w: W - 0.6, h: 0.7,
      fontSize: 13, fontFace: FONT,
      color: C.gray, align: "center", valign: "middle", margin: 0,
    });
  }

  // ── Write file ─────────────────────────────────────────────────────────
  await pres.writeFile({ fileName: OUT_FILE });
  console.log("SUCCESS: Written to", OUT_FILE);
}

build().catch((err) => { console.error("ERROR:", err); process.exit(1); });
