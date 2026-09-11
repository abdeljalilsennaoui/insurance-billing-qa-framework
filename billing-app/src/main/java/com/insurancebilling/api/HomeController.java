package com.insurancebilling.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sends the application root to the invoice console, so the base URL is a usable entry point. */
@Controller
public class HomeController {

  @GetMapping("/")
  public String home() {
    return "redirect:/invoices";
  }
}
