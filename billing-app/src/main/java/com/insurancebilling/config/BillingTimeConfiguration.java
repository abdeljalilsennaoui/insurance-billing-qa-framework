package com.insurancebilling.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for the billing domain.
 *
 * <p>Every business date decision in this application — above all whether an invoice is
 * {@code OVERDUE} — is made against this clock. Before it existed the decision was made by
 * {@code LocalDate.now()}, which reads the JVM's default zone: the business date was then a property
 * of whichever host the process happened to be started on, and moving the deployment changed invoice
 * statuses by up to a day with nothing in the code to explain it. See DEF-012 in
 * {@code docs/defect-reports.md}.
 *
 * <p>The zone is configuration, not a constant, because it is a business decision rather than a
 * technical one: it is the zone the insurer's billing day is reckoned in. It defaults to
 * {@code America/Toronto} — this is a Canadian property and casualty billing simulation — and is
 * overridden with {@code billing.time-zone}.
 *
 * <p>An unknown zone id fails the context on startup with a {@code DateTimeException} naming it. That
 * is deliberate: a typo that silently fell back to the system default would restore exactly the
 * defect this bean exists to remove.
 *
 * <p>The bean is a {@link Clock} rather than a bare {@link ZoneId} so that tests can freeze it. A
 * zone alone leaves the instant coming from the machine, which makes any date-dependent behaviour
 * testable only in terms of offsets from "today"; a frozen clock lets a test assert absolute dates
 * and pin the boundary where two zones disagree.
 */
@Configuration
public class BillingTimeConfiguration {

  @Bean
  public Clock businessClock(@Value("${billing.time-zone:America/Toronto}") String zoneId) {
    return Clock.system(ZoneId.of(zoneId));
  }
}
