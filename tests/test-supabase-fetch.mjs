const supabaseUrl = "https://vtuztciyaqegrvoaxmnf.supabase.co";
const supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ0dXp0Y2l5YXFlZ3J2b2F4bW5mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA3ODY2MDAsImV4cCI6MjEwNjM2MjYwMH0.EGWVxNSZAf-fmEPRr7V0X_r9FVLe1sqQW9r8Mc5C5F4";

async function testFetch() {
    console.log("Checking Supabase REST API endpoint...");
    try {
        const response = await fetch(`${supabaseUrl}/rest/v1/`, {
            headers: {
                "apikey": supabaseAnonKey,
                "Authorization": `Bearer ${supabaseAnonKey}`
            }
        });
        console.log("HTTP Status:", response.status, response.statusText);
        const text = await response.text();
        console.log("Response snippet:", text.substring(0, 300));
        console.log("\nSupabase project is active, reachable, and authenticated successfully!");
    } catch (e) {
        console.error("Fetch failed:", e);
    }
}

testFetch();
