package com.flightticketspricetracker;

import java.util.LinkedHashMap;
import java.util.Map;

/** Moroccan Darija UI localization. Proper names, airport codes, currencies and provider data stay unchanged. */
public final class Darija {
    private static final Map<String, String> EXACT = new LinkedHashMap<>();
    private static final Map<String, String> PARTS = new LinkedHashMap<>();

    static {
        exact("FLIGHT TRACKER", "من الحمارة للطيارة");
        exact("LIVE  •  ACCURATE  •  GLOBAL", "الرحلات والأثمنة دابا");
        exact("Provider", "المزوّد");
        exact("Configure", "الإعدادات");
        exact("Ready to track", "واجد باش تقلّب");
        exact("Search live provider inventory to compare verified fare offers.", "قلّب فالعروض الحقيقية ديال الرحلات وقارن الأثمنة الموثوقة.");
        exact("REAL", "حقيقي");
        exact("SMART", "ذكي");
        exact("ZERO", "صفر");
        exact("Provider fares", "أثمنة حقيقية");
        exact("Price alerts", "تنبيهات الثمن");
        exact("Fake fallback", "أثمنة مخترعة");
        exact("Verified provider data only. Errors stay honest, and generated fares never replace real results.",
                "غير معلومات حقيقية من المزوّد. إلا وقع شي مشكل غادي نقولوها بصراحة، وما غاديش نعوّضو النتائج بأثمنة مخترعة.");
        exact("Track a route", "تبّع مسار");
        exact("Search current flight offers", "قلّب فالعروض ديال الرحلات دابا");
        exact("ORIGIN", "منين");
        exact("DESTINATION", "لفين");
        exact("Airport, city, or IATA code", "المطار، المدينة ولا كود IATA");
        exact("Swap route", "بدّل الاتجاه");
        exact("Round trip", "رايح جاي");
        exact("Nonstop only", "غير بلا توقّف");
        exact("DEPART", "الذهاب");
        exact("RETURN", "الرجوع");
        exact("TRAVELLERS", "المسافرين");
        exact("CABIN", "الدرجة");
        exact("CURRENCY", "العملة");
        exact("Economy", "إيكونومي");
        exact("Premium Economy", "بريميوم إيكونومي");
        exact("Business", "بزنس");
        exact("First", "فيرست كلاس");
        exact("Search live flights", "قلّب على الرحلات دابا");
        exact("Watch a verified total fare", "راقب الثمن الحقيقي اللي بغيتي");
        exact("TARGET TOTAL PRICE", "الثمن اللي باغي");
        exact("Save alert", "حفظ التنبيه");
        exact("Check now", "شوف دابا");
        exact("Clear alerts and price history", "مسح التنبيهات وتاريخ الأثمنة");
        exact("Track", "التتبّع");
        exact("Flights", "الرحلات");
        exact("Airports", "المطارات");
        exact("Alerts", "التنبيهات");
        exact("More", "كثر");
        exact("CONNECTION MODE", "طريقة الربط");
        exact("SerpApi on this device", "SerpApi فهاد التلفون");
        exact("Secure backend", "سيرفر آمن");
        exact("SECURE BACKEND URL", "رابط السيرفر الآمن");
        exact("BACKEND ACCESS TOKEN, OPTIONAL", "توكن ديال السيرفر، اختياري");
        exact("SERPAPI API KEY", "مفتاح SerpApi");
        exact("For private use, choose SerpApi on this device and paste one API key. The key is stored with Android Keystore. Secure backend mode remains available for distributed builds.",
                "إلا غادي تستعمل التطبيق بوحدك، اختار SerpApi فهاد التلفون وحط مفتاح API واحد. المفتاح كيتخزّن بأمان فـ Android Keystore. السيرفر الآمن باقِي متاح إلا بغيتي توزّع التطبيق.");
        exact("Configure real flight data", "إعدادات الرحلات الحقيقية");
        exact("Cancel", "رجع");
        exact("Clear", "مسح");
        exact("Save", "حفظ");
        exact("Provider configuration saved securely.", "الإعدادات تحفظات بأمان.");
        exact("Configure a live provider first.", "خصك تصايب المزوّد ديال الرحلات اللول.");
        exact("Searching provider inventory...", "كنقلّبو على الرحلات دابا...");
        exact("Provider test results", "نتائج التجربة ديال المزوّد");
        exact("Live provider results", "النتائج الحقيقية");
        exact("No offers returned", "ما لقينا حتى عرض");
        exact("The provider did not return a fare for this route and date combination.", "المزوّد ما رجّع حتى ثمن لهاد المسار وهاد التاريخ.");
        exact("Outbound", "الذهاب");
        exact("Return", "الرجوع");
        exact("Baggage", "الباكاج");
        exact("Tap this offer to use its total as your alert target", "كليكي على هاد العرض باش تستعمل الثمن ديالو كهدف للتنبيه");
        exact("Search unavailable", "البحث ما خدامش دابا");
        exact("The provider returned an error", "المزوّد رجّع مشكل");
        exact("Flight search failed.", "البحث على الرحلات ما خدمش.");
        exact("Configure a live provider before saving alerts.", "صايب المزوّد ديال الرحلات قبل ما تحفظ التنبيهات.");
        exact("Enter a valid target amount.", "دخل ثمن صحيح.");
        exact("Live price alert saved. Android will check it about every six hours when permitted.", "التنبيه تحفظ. Android غادي يشوف الثمن تقريباً كل 6 سوايع إلا كان مسموح ليه.");
        exact("Save an alert first.", "حفظ شي تنبيه اللول.");
        exact("Checking saved alerts against provider inventory...", "كنشوفو التنبيهات المحفوظة مع أثمنة المزوّد...");
        exact("No saved alerts", "ما كاين حتى تنبيه محفوظ");
        exact("Choose a target price and save it to begin monitoring.", "اختار الثمن اللي باغي وحفظو باش نبداو نراقبوه.");
        exact("Waiting for first provider check", "كنتسناو أول فحص ديال الثمن");
        exact("Target reached", "وصل للثمن اللي بغيتي");
        exact("Watching", "كنراقبو");
        exact("Delete", "مسح");
        exact("There are no alerts to clear.", "ما كاين حتى تنبيه باش يتمسح.");
        exact("Clear all alerts?", "نمسحو التنبيهات كاملين؟");
        exact("This removes every alert and its recorded provider prices.", "هادشي غادي يمسح التنبيهات كاملين والأثمنة اللي تسجلات معاهم.");
        exact("Something went wrong.", "وقع شي مشكل.");
        exact("Nonstop", "بلا توقّف");
        exact("1 stop", "توقّف واحد");
        exact("Unknown carrier", "شركة الطيران ما بايناش");
        exact("Flight number unavailable", "رقم الرحلة ما متوفرش");
        exact("Route unavailable", "المسار ما متوفرش");
        exact("Outbound itinerary unavailable", "تفاصيل الذهاب ما متوفراش");
        exact("Baggage not specified by provider", "المزوّد ما وضّحش الباكاج");
        exact("Flight price alerts", "تنبيهات أثمنة الطيارات");
        exact("Notifications when a real provider fare reaches a saved target.", "تنبيهات ملي الثمن الحقيقي يوصل للهدف اللي حفظتي.");
        exact("LIVE", "مباشر");

        part("NOT CONFIGURED", "مازال ما تصايبش");
        part("Add a free SerpApi key", "زيد مفتاح SerpApi مجاني");
        part("SECURE BACKEND", "سيرفر آمن");
        part("GOOGLE FLIGHTS VIA SERPAPI", "Google Flights عبر SerpApi");
        part("API key stored on this device", "مفتاح API مخزّن فهاد التلفون");
        part("Enter the HTTPS URL of the flight backend.", "دخل رابط HTTPS ديال سيرفر الرحلات.");
        part("The backend URL must use HTTPS.", "رابط السيرفر خاصو يبدا بـ HTTPS.");
        part("Enter your SerpApi API key.", "دخل مفتاح SerpApi ديالك.");
        part("Enter a valid 3-letter origin IATA code.", "دخل كود IATA صحيح من 3 حروف ديال مطار الانطلاق.");
        part("Enter a valid 3-letter destination IATA code.", "دخل كود IATA صحيح من 3 حروف ديال مطار الوصول.");
        part("Origin and destination must be different.", "الانطلاق والوصول ما يقدروش يكونو نفس المطار.");
        part("Passengers must be between 1 and 9.", "عدد المسافرين خاصو يكون بين 1 و9.");
        part("Departure date must use YYYY-MM-DD.", "تاريخ الذهاب خاصو يكون بهاد الشكل YYYY-MM-DD.");
        part("Departure date cannot be in the past.", "تاريخ الذهاب ما يقدرش يكون فات.");
        part("Return date must use YYYY-MM-DD for a round trip.", "فالرايح جاي، تاريخ الرجوع خاصو يكون YYYY-MM-DD.");
        part("Return date cannot be before departure.", "تاريخ الرجوع ما يقدرش يكون قبل الذهاب.");
        part("Flight target reached:", "الثمن اللي بغيتي وصل:");
        part(" with ", " مع ");
        part("Outbound: ", "الذهاب: ");
        part("Return: ", "الرجوع: ");
        part(" total", " المجموع");
        part("Checked ", "تشاف فـ ");
        part("Latest ", "آخر ثمن ");
        part(" offers", " عروض");
        part(" offer", " عرض");
        part(" stops", " توقّفات");
        part(" stop", " توقّف");
        part(" traveller", " مسافر");
        part(" travellers", " مسافرين");
        part("nonstop", "بلا توقّف");
        part("Economy", "إيكونومي");
        part("Premium Economy", "بريميوم إيكونومي");
        part("Business", "بزنس");
        part("First", "فيرست كلاس");
        part("Target set to ", "الثمن المستهدف ولى ");
        part("Checked ", "تفحصو ");
        part(" alerts", " تنبيهات");
        part(" alert", " تنبيه");
        part(" targets reached.", " أهداف وصلو للثمن.");
        part(" target reached.", " هدف وصل للثمن.");
    }

    private Darija() {}

    private static void exact(String from, String to) {
        EXACT.put(from, to);
    }

    private static void part(String from, String to) {
        PARTS.put(from, to);
    }

    public static String text(CharSequence value) {
        if (value == null) return "";
        String input = value.toString();
        String direct = EXACT.get(input);
        if (direct != null) return direct;
        String output = input;
        for (Map.Entry<String, String> entry : PARTS.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }
}
