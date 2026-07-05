package com.mls.logistics.logistics;

import com.mls.logistics.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: the full application context starts against a real PostgreSQL
 * (Testcontainers), which also exercises the Flyway migrations and the
 * Hibernate schema validation on a clean database.
 */
class LogisticsApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
