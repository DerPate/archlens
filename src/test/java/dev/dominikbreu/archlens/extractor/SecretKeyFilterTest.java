package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretKeyFilterTest {

    @Test
    void flagsAllEightMarkers() {
        assertThat(SecretKeyFilter.isSecretKey("spring.datasource.password")).isTrue();
        assertThat(SecretKeyFilter.isSecretKey("spring.datasource.username")).isTrue();
        assertThat(SecretKeyFilter.isSecretKey("app.credential.store")).isTrue();
        assertThat(SecretKeyFilter.isSecretKey("app.secret.value")).isTrue();
        assertThat(SecretKeyFilter.isSecretKey("spring.datasource.token")).isTrue();
        assertThat(SecretKeyFilter.isSecretKey("service.apikey")).isTrue();
        assertThat(SecretKeyFilter.isSecretKey("service.api-key")).isTrue();
        assertThat(SecretKeyFilter.isSecretKey("service.private-key")).isTrue();
    }

    @Test
    void doesNotFlagUnrelatedKeys() {
        assertThat(SecretKeyFilter.isSecretKey("billing.client.base-url")).isFalse();
        assertThat(SecretKeyFilter.isSecretKey("quarkus.rest-client.billing.url"))
                .isFalse();
    }
}
