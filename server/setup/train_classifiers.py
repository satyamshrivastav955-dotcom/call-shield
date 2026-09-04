"""Train the fine-tuned text classifiers (scam-pattern, urgency, intent).

Produces checkpoints at models/classifiers/{scam_pattern,urgency,intent}
from a synthetic bilingual (English + Hindi-romanized/Devanagari) corpus.
These replace the keyword heuristics used when weights are absent.

Usage:
    conda run -n antai-server python setup/train_classifiers.py [--task all|scam_pattern|urgency|intent] [--epochs 3]

Label schemes (must match src/antai/inference engines):
  scam_pattern: [benign, family_emergency, otp, bank, prize, job_offer,
                 romance, remote_access, gift_card]   (9 labels)
  urgency:      single regression logit -> 0..1 urgency score
  intent:       [money, otp, credential, remote-access, link, none] (6 labels)
"""
from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "models" / "classifiers"

SCAM_TYPES = ["family_emergency", "otp", "bank", "prize", "job_offer",
              "romance", "remote_access", "gift_card"]
INTENT_TYPES = ["money", "otp", "credential", "remote-access", "link", "none"]

rng = random.Random(20260821)

# ---------------------------------------------------------------------------
# corpus templates (English + Hinglish + Devanagari)
# ---------------------------------------------------------------------------

SCAM_TEMPLATES: dict[str, list[str]] = {
    "family_emergency": [
        "Your son is in an accident, send money to the hospital right now",
        "Your daughter is in police custody, pay the bail immediately",
        "Beta is in the hospital, send money urgently for treatment",
        "Your son met with an accident and is in hospital, transfer money now",
        "Police have arrested your son, deposit the fine immediately",
        "Mummy is in hospital, send money right away for her surgery",
        "Your daughter has been kidnapped, pay the ransom now",
        "Beta pakda gaya hai, turant paise bhejo",
        "Aapka beta hospital me hai, abhi paise transfer karo",
        "Beti ko police ne pakda hai, bail ke liye paise chahiye",
        "आपका बेटा अस्पताल में है, तुरंत पैसे भेजो",
        "बेटी को पुलिस ने पकड़ा है, जमानत के लिए पैसे भेजो",
        "Beta accident me hai, hospital me paise bhejo jaldi",
        "Your grandpa is in hospital after an accident, send money for medicine",
        "Beti hospital me hai, operation ke liye paise chahiye abhi",
        "I am stuck at the police station after an accident, send money immediately",
        "My phone and account are blocked, transfer money to this account now",
        "I met with an accident and am in trouble, deposit bail money today",
        "Police station me phas gaye hain, turant paise transfer karo",
        "Account block ho gaya hai, is account me paise bhejo",
        "Accident ho gaya hai, police ne pakda hai, bail ke liye paise chahiye",
        "मैं पुलिस स्टेशन में फंस गया हूँ, तुरंत पैसे भेजो",
        "My phone was stolen, send money to this account for my release",
        "I am your son, I am stuck at the police station, transfer 50000 rupees now",
        "Beta police custody me hai, account blocked hai, paise bhejo turant",
        "Do not tell anyone about this, I am at the police station, send money",
        "Mummy please send money urgently, I am at the police station",
        "Police station me hu, bail ke liye turant paise chahiye, kisi ko mat batana",
    ],
    "otp": [
        "I am from the bank, please share the OTP to verify your account",
        "Share the one-time password to unblock your account now",
        "Send the verification code to confirm your transaction",
        "Your card is blocked, send the OTP to reactivate it",
        "KYC update ke liye OTP share karo",
        "Account verify karne ke liye OTP batao abhi",
        "Apna OTP code bhej do, bank call kar raha hai",
        "आपके खाते की पुष्टि के लिए OTP भेजें",
        "Card unblock karne ke liye verification code batao",
        "Bank se call hai, OTP number batao turant",
        "Share the 6 digit code you just received on your phone",
        "Your account will be frozen, send the OTP immediately",
        "OTP bhejo account security ke liye, jaldi karo",
        "Aadhaar link karne ke liye OTP share karna hoga",
    ],
    "bank": [
        "Your account has been frozen due to fraud, contact this number",
        "Your card is blocked for suspicious activity, call immediately",
        "Your bank account is suspended, update KYC today",
        "There is fraudulent activity on your account, act now",
        "Your account will be closed in 24 hours unless you respond",
        "Aapka bank account freeze ho gaya hai, turant action lo",
        "Card blocked ho gaya hai, bank call karo abhi",
        "आपका बैंक खाता फ्रीज हो गया है, तुरंत संपर्क करें",
        "Bank me fraud hua hai aapke account me, jaldi karo",
        "Your KYC is incomplete, your account will be suspended",
        "Account me suspicious activity hai, verify karo turant",
        "Your internet banking will be disabled unless you confirm",
        "KYC update nahi kiya to account band ho jayega",
        "Fraud transaction hua hai, bank se confirm karo abhi",
    ],
    "prize": [
        "Congratulations! You have won a lottery of 50 lakh rupees",
        "You are the lucky winner of our mega jackpot, claim now",
        "Your phone number won a prize, click the link to claim",
        "You won 10 lakh in the lucky draw, pay the processing fee",
        "Winner bane ho aap, prize claim karne ke liye fee bhejo",
        "Aapne lottery jeeta hai, claim ke liye abhi contact karo",
        "आपने लॉटरी जीती है, पुरस्कार लेने के लिए संपर्क करें",
        "Lucky draw me aapka number select hua hai, fee bhejo",
        "Congratulations, you won a gift voucher, verify your details",
        "Jackpot jeeta hai aapne, processing charges pay karo",
        "Prize money claim karne ke liye bank details bhejo",
        "Aap 25 lakh ke winner ho, abhi register karo",
    ],
    "job_offer": [
        "Earn 5000 rupees daily working from home, join now",
        "High paying part time job, pay registration fee to start",
        "Work from home job, earn commission per task",
        "Telegram job: complete tasks and earn money, join now",
        "Part time job me kamai, registration ke liye paise lagenge",
        "Work from home me 1000 daily, fee bhejo aur join karo",
        "घर बैठे काम करके 5000 रुपये रोज़ कमाएं, अभी जुड़ें",
        "Telegram pe task karo aur paise kamao, abhi join karo",
        "Earning job for students, deposit advance for kit",
        "Paid tasks on app, install and earn, register first",
        "Commission wala kaam hai, processing fee bhejo",
        "Simple job, earn from mobile, pay training fee to start",
    ],
"romance": [
        "I love you so much, please send me money for my flight ticket",
        "Darling, I need money urgently for my hospital treatment",
        "I am stranded abroad, please transfer money to my account",
        "Sweetheart, send money for the visa so I can come to you",
        "Meri jaan, mujhe paise chahiye emergency me",
        "I love you, please pay my fee or I will be in trouble",
        "प्यारे, मुझे जरूरत है पैसों की, अभी भेजो",
        "I want to marry you but first help me with this payment",
        "Baby, I lost my wallet, send me money now",
        "Mujhe tumhari bahut zaroorat hai, paise bhej do",
        "I am in trouble abroad, only you can help me, send money",
        "Dear, my father is ill, send money for his medicine",
    ],
    "remote_access": [
        "Install this app so I can help you with your problem",
        "Download TeamViewer and give me the ID and password",
        "Share your screen so I can fix your bank issue",
        "Install AnyDesk to verify your account safely",
        "App install karo aur screen share karo, account theek ho jayega",
        "TeamViewer download karo aur ID password batao",
        "अपनी स्क्रीन शेयर करो ताकि मैं आपकी समस्या ठीक कर सकूं",
        "Bank wala app install karo mujhe access dene ke liye",
        "AnyDesk install karo, support team connect karegi",
        "Download this app and allow remote access to update your KYC",
        "Screen share karo, hum aapka card unblock kar denge",
        "Install the security app and share the access code",
    ],
    "gift_card": [
        "Buy an Amazon gift card and share the code for delivery",
        "Send a Google Play gift card code to receive your refund",
        "Buy iTunes cards and send the numbers for your prize",
        "Purchase a gift card to pay the customs fee",
        "Amazon gift card kharido aur code bhejo",
        "Google Play card ka code batao, aapka refund aa jayega",
        "गिफ्ट कार्ड खरीदकर कोड भेजें, डिलीवरी हो जाएगी",
        "Prize ke liye gift card ke code chahiye, jaldi bhejo",
        "Buy gift cards to unlock your reward, share the numbers",
        "Gift card code bhejo, customs clearance ke liye",
        "Refund ke liye app store card ka code batao",
        "Send the gift card numbers to complete your order",
    ],
}

BENIGN_TEMPLATES = [
    "Okay, I will call you in the evening",
    "Please buy vegetables on your way home",
    "The weather is nice today, let's go for a walk",
    "Did you see the news today?",
    "I will send you the photos from the wedding",
    "What time is dinner ready?",
    "Let's meet at the park tomorrow morning",
    "I booked the tickets for Sunday",
    "The repair man will come at 4 pm",
    "Can you remind me about the doctor's appointment?",
    "Mummy ne dinner ke liye bulaya hai",
    "Kal sham ko ghar aana",
    "Aaj bahut garmi hai, paani piyo",
    "मैं शाम को वापस आऊंगा",
    "दादी ने खाना बना दिया है",
    "Tickets book kar liye hain Sunday ke",
    "Doctor ka appointment 4 baje hai",
    "Neighbor ke bachche school me jeet gaye",
    "I will pick you up from the station at 6",
    "Thanks for the birthday wishes",
    "The movie starts at 8, don't be late",
    "घर पर सब ठीक है, चिंता मत करो",
    "Sabzi le aana raste se",
    "Tumhara message mil gaya, theek hai",
    "I am watering the plants, will call later",
    "The shop is closed today, we can go tomorrow",
    "Aunty ne puchha tha tum kab aaoge",
    "I made tea, do you want some?",
    "कल सुबह मिलते हैं पार्क में",
    "Remember to take your medicines on time",
]

PRESSURE_TEMPLATES = [
    "Send the money right now, it is urgent",
    "This must be done immediately, do not delay",
    "Act now before it is too late",
    "Do not tell anyone about this, keep it secret",
    "The deadline is today, you must act fast",
    "Time is running out, confirm right away",
    "Your account will be closed in one hour if you do not respond",
    "Abhi karo, der mat karo",
    "Turant action lo, waqt kam hai",
    "Kisi ko mat batana, ye confidential hai",
    "आज ही करना होगा, कल नहीं",
    "Aakhri warning hai, abhi confirm karo",
    "Within an hour everything will be deleted",
    "Pay the fine today or legal action will be taken",
    "This is your final chance, respond immediately",
    "Police case file ho jayega agar abhi nahi kiya",
    "Ek ghante me account band ho jayega",
    "Immediately share the code or the transaction will fail",
]

CALM_TEMPLATES = [
    "Let me know when you are free to talk",
    "We can discuss this whenever you have time",
    "Take your time, there is no hurry",
    "I will wait for your reply",
    "Whenever you are ready, let me know",
    "No rush, we can decide later",
    "Jab time mile tab baat karte hain",
    "Fikar mat karo, sab theek ho jayega",
    "कोई जल्दी नहीं है, सोच कर बताओ",
    "We can finish this tomorrow",
    "I will check and get back to you",
    "It's fine, we can handle it slowly",
    "Koi problem nahi, hum baad me dekhenge",
    "Let me know your convenience",
]

INTENT_TEMPLATES: dict[str, list[str]] = {
    "money": [
        "Please send money to this account now",
        "Transfer 20000 rupees to this UPI ID",
        "Send me money through GPay right now",
        "Deposit cash into my account immediately",
        "Money transfer karo is account me",
        "Paise bhej do abhi, urgent hai",
        "पैसे भेजो इस खाते में तुरंत",
        "Send 5000 via Paytm to this number",
        "Do a bank transfer to this account today",
        "Rupaye transfer kar do abhi",
        "Please pay my hospital bill today",
        "Give me the money by tonight",
    ],
    "otp": [
        "Share the OTP to verify the transaction",
        "Send the one-time password you received",
        "Batao OTP kya aaya hai",
        "OTP share karo account verify karne ke liye",
        "अभी OTP भेजो",
        "Tell me the 6 digit code from your SMS",
        "Send the verification code from the bank message",
        "OTP bhej do jo SMS me aaya hai",
        "What is the OTP you just received?",
        "Share the code to confirm the payment",
    ],
    "credential": [
        "Give me your internet banking password",
        "Send your login ID and password",
        "I need your account number and card details",
        "Password batao aapke bank ka",
        "अपना यूज़र आईडी और पासवर्ड भेजो",
        "Share your Aadhaar number and PAN",
        "Tell me your CVV to complete the refund",
        "Account number aur password bhejo",
        "Give me your username for the portal",
        "Apna OTP aur card number batao",
    ],
    "remote-access": [
        "Install TeamViewer and give me the ID",
        "Download AnyDesk and share the access code",
        "Install this app and allow screen sharing",
        "TeamViewer install karo aur ID batao",
        "स्क्रीन शेयर करो एक्सेस देने के लिए",
        "Share your screen so I can help you",
        "Allow remote access from this app",
        "AnyDesk download karo abhi",
        "Give me remote access to fix the issue",
        "App install karo aur permission do",
    ],
    "link": [
        "Click this link to claim your reward",
        "Open the link I sent you",
        "Tap this link to verify your account",
        "Click here to download the app",
        "Link par click karo abhi",
        "इस लिंक पर क्लिक करो",
        "Visit the website I shared with you",
        "Open the payment link and pay",
        "Click the link to update your details",
        "Download link bhej raha hoon, click karo",
    ],
}

NONE_TEMPLATES = [
    "How are you doing today",
    "Let's meet this weekend",
    "The food was really good yesterday",
    "I am going to the market now",
    "What are you having for lunch",
    "Did you finish the work",
    "Aaj kya khana banega",
    "मैं बाजार जा रहा हूँ",
    "Kya kar rahe ho aaj",
    "The game starts at 7 tonight",
    "I will see you later",
    "Thanks for your help",
    "Tumhara din kaisa raha",
    "She said she will come tomorrow",
    "It is raining outside",
    "Let me know when you reach home",
]

# ---------------------------------------------------------------------------
# corpus builders
# ---------------------------------------------------------------------------


def _vary(s: str, pool: list[str] | None = None) -> str:
    words = s.split()
    for _ in range(rng.randint(0, 1)):
        if words:
            i = rng.randrange(len(words))
            words[i] = rng.choice(pool or ["please", "beta", "ji", "ok", "quickly",
                                            "kal", "abhi", "sir"])
    return " ".join(words)


def build_scam_corpus(n_per_class: int = 350, n_benign: int = 1400):
    rows = []
    for stype, tpls in SCAM_TEMPLATES.items():
        for _ in range(n_per_class):
            base = rng.choice(tpls)
            rows.append((_vary(base), stype))
    for _ in range(n_benign):
        rows.append((rng.choice(BENIGN_TEMPLATES), "benign"))
    rng.shuffle(rows)
    return rows


def build_urgency_corpus(n_high: int = 700, n_low: int = 700):
    rows = []
    for _ in range(n_high):
        t = rng.choice(PRESSURE_TEMPLATES + [s for v in SCAM_TEMPLATES.values() for s in v])
        rows.append((_vary(t), 1.0))
    for _ in range(n_low):
        rows.append((rng.choice(CALM_TEMPLATES + BENIGN_TEMPLATES), 0.0))
    rng.shuffle(rows)
    return rows


def build_intent_corpus(n_per_class: int = 250, n_none: int = 900):
    rows = []
    for itype, tpls in INTENT_TEMPLATES.items():
        for _ in range(n_per_class):
            rows.append((_vary(rng.choice(tpls)), itype))
    for _ in range(n_none):
        rows.append((rng.choice(NONE_TEMPLATES + CALM_TEMPLATES), "none"))
    rng.shuffle(rows)
    return rows


# ---------------------------------------------------------------------------
# training
# ---------------------------------------------------------------------------

def train_task(task: str, epochs: int):
    import torch
    from torch.utils.data import DataLoader, TensorDataset
    from transformers import AutoModelForSequenceClassification, AutoTokenizer

    base = str(ROOT / "models" / "classifier_base")
    print(f"[{task}] loading base model from {base}")
    tokenizer = AutoTokenizer.from_pretrained(base)

    if task == "scam_pattern":
        labels = ["benign"] + SCAM_TYPES
        corpus = build_scam_corpus()
    elif task == "intent":
        labels = INTENT_TYPES
        corpus = build_intent_corpus()
    elif task == "urgency":
        labels = None
        corpus = build_urgency_corpus()
    else:
        raise SystemExit(f"unknown task {task}")

    def tok(texts):
        return tokenizer(texts, truncation=True, max_length=128,
                         padding=True, return_tensors="pt")

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    if task == "urgency":
        texts = [t for t, _ in corpus]
        labels_t = torch.tensor([v for _, v in corpus], dtype=torch.float32)
        enc = tok(texts)
        model = AutoModelForSequenceClassification.from_pretrained(base, num_labels=1)
        loss_fn = torch.nn.MSELoss()
    else:
        label_to_id = {l: i for i, l in enumerate(labels)}
        texts = [t for t, _ in corpus]
        labels_t = torch.tensor([label_to_id[l] for _, l in corpus], dtype=torch.long)
        enc = tok(texts)
        model = AutoModelForSequenceClassification.from_pretrained(
            base, num_labels=len(labels), id2label={i: l for i, l in enumerate(labels)},
            label2id=label_to_id)
        loss_fn = torch.nn.CrossEntropyLoss()

    dataset = TensorDataset(enc["input_ids"], enc["attention_mask"], labels_t)
    loader = DataLoader(dataset, batch_size=16, shuffle=True)
    model.to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-5, weight_decay=0.01)

    out_dir = OUT_DIR / task
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"[{task}] training {len(corpus)} samples x {epochs} epochs on {device}")
    for epoch in range(1, epochs + 1):
        model.train()
        total, n = 0.0, 0
        for input_ids, attn, y in loader:
            input_ids, attn, y = input_ids.to(device), attn.to(device), y.to(device)
            optimizer.zero_grad()
            logits = model(input_ids=input_ids, attention_mask=attn).logits
            if task == "urgency":
                loss = loss_fn(logits.squeeze(-1), y)
            else:
                loss = loss_fn(logits, y)
            loss.backward()
            optimizer.step()
            total += loss.item()
            n += 1
        print(f"[{task}] epoch {epoch}/{epochs} loss={total / max(1, n):.4f}")

    model.save_pretrained(str(out_dir))
    tokenizer.save_pretrained(str(out_dir))
    print(f"[{task}] saved -> {out_dir}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--task", default="all",
                    choices=["all", "scam_pattern", "urgency", "intent"])
    ap.add_argument("--epochs", type=int, default=3)
    args = ap.parse_args()

    # ensure base classifier is downloaded
    base_dir = ROOT / "models" / "classifier_base"
    if not (base_dir / "config.json").exists():
        print("downloading distilbert-base-multilingual-cased ...")
        from huggingface_hub import snapshot_download
        snapshot_download(repo_id="distilbert-base-multilingual-cased",
                          local_dir=str(base_dir), local_dir_use_symlinks=False)

    tasks = ["scam_pattern", "urgency", "intent"] if args.task == "all" else [args.task]
    for t in tasks:
        train_task(t, args.epochs)


if __name__ == "__main__":
    main()