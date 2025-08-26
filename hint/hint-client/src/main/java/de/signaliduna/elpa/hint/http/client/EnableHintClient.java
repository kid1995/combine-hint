package de.signaliduna.elpa.hint.http.client;

import de.signaliduna.elpa.hint.http.client.config.HintClientConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables the hint-client library to configure itself and add the {@link de.signaliduna.elpa.hint.http.client.HintClient}
 * to the application context.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(HintClientConfig.class)
public @interface EnableHintClient {
}
