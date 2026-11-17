# Nova AI — Offline Voice Assistant (Android, Kotlin + Jetpack Compose)

## Chalane ke steps
1. Android Studio (Koala ya naya) mein `NovaAI` folder kholo, Gradle sync hone do.
2. Phone par run karo (Android 8.0+). Permissions allow karo (mic, call, contacts).
3. **Offline voice (Hindi):** Phone Settings > Google > (Voice) / Speech services mein Hindi + English offline pack download karo.
4. **Offline AI dimaag:** ek MediaPipe LLM-compatible `.task` model (jaise Gemma 1B int4) download karo, uska naam `model.task` rakho, aur phone ke
   `Android/data/com.nova.ai/files/` folder mein daalo. Bina model ke bhi app chalta hai (time, alarm, math, call, app, music, mausam, Wikipedia).

## Kya bolein
- "abhi kitne baje hain" / "aaj ki tareekh"
- "subah 6:30 ka alarm" / "10 minute ka timer" / "5 minute baad yaad dilao"
- "mera shehar Pune hai" → "mausam batao"
- "WhatsApp kholo" / "Arijit ke gaane chalao" / "youtube par cricket video chalao"
- "Rahul ko call karo" / "Rahul ko message bhejo ki main late hoon"
- "25 gunaa 4" / "(12+8)/5"
- "yaad rakho ki mujhe dawa 9 baje leni hai" → "kya yaad hai"
- 📷 dabao, photo lo, phir poochho "isme kya likha hai?"
- Baaki koi bhi sawal / padhai ki help → offline model jawab deta hai

## Notes
- SMS ka composer khulta hai aur aap Send dabate ho (Play Store policy ke hisaab se safe tareeka).
- Mausam aur Wikipedia search ko internet chahiye; baaki sab offline.
- Camera sawal: ML Kit objects + text pehchanta hai, phir model jawab deta hai (poora vision model nahi).
