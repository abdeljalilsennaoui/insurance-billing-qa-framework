package com.insurancebilling.qa.bdd.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.Test;

/** Runs the browser-level scenarios. */
@Test
@CucumberOptions(
    features = "classpath:features",
    // Both packages are listed deliberately. Glue scanning is package-exact, not recursive from a
    // parent, so naming only 'steps' leaves the hooks in 'support' unregistered: the @Before("@ui")
    // hook never fires and every browser scenario dies on the first page object with "No WebDriver
    // for this thread". Cucumber reports that as a step failure rather than as missing glue, which
    // makes it look like a driver bug instead of a configuration one.
    glue = {"com.insurancebilling.qa.bdd.steps", "com.insurancebilling.qa.bdd.support"},
    tags = "@ui",
    plugin = {
      "pretty",
      "html:target/cucumber-reports/ui.html",
      "json:target/cucumber-reports/ui.json",
      "summary"
    })
public class UiScenariosIT extends AbstractTestNGCucumberTests {}
