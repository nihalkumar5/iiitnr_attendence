import { createClient } from "@supabase/supabase-js";

const supabaseUrl = "https://vtuztciyaqegrvoaxmnf.supabase.co";
const supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ0dXp0Y2l5YXFlZ3J2b2F4bW5mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA3ODY2MDAsImV4cCI6MjEwNjM2MjYwMH0.EGWVxNSZAf-fmEPRr7V0X_r9FVLe1sqQW9r8Mc5C5F4";

const supabase = createClient(supabaseUrl, supabaseAnonKey);

async function testConnection() {
    console.log("Testing connection to Supabase project:", supabaseUrl);
    try {
        const { data, error } = await supabase.from('institutions').select('*').limit(1);
        if (error) {
            console.log("Response from Supabase:", error.message, "(Code:", error.code + ")");
            console.log("Supabase endpoint is reachable and responsive!");
        } else {
            console.log("Successfully queried Supabase! Records:", data);
        }
    } catch (e) {
        console.error("Connection failed:", e);
    }
}

testConnection();
