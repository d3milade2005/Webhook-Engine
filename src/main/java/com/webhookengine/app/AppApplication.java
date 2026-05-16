package com.webhookengine.app;

import org.aspectj.runtime.CFlow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//The system CFlow
//1. Someone calls POST /events
//        ↓
//2. Save to database (status = PENDING)
//        ↓
//3. Pick it up, send HTTP POST to customer's URL
//		↓
//4. Did it work?
//     YES → mark DELIVERED, done
//     NO  → wait, try again later
//        ↓
//5. Tried 5 times and still failing?
//		→ mark DEAD, alert someone

@SpringBootApplication
public class AppApplication {
	public static void main(String[] args) {
		SpringApplication.run(AppApplication.class, args);
	}
}
