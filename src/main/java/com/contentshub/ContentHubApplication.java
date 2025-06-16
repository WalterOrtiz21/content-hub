package com.contentshub;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@SpringBootApplication
@EnableWebFlux
@EnableAsync
@EnableScheduling
@EnableR2dbcAuditing
@EnableR2dbcRepositories(basePackages = "com.contentshub.infrastructure.persistence.r2dbc")
@EnableReactiveMongoAuditing
@EnableReactiveMongoRepositories(basePackages = "com.contentshub.infrastructure.persistence.mongodb")
@EnableRedisRepositories(basePackages = "com.contentshub.infrastructure.persistence.redis")
@OpenAPIDefinition(
		info = @Info(
				title = "RealTime Content Hub API",
				version = "1.0.0",
				description = "Sistema de gestión de contenido colaborativo en tiempo real",
				contact = @Contact(
						name = "Content Hub Team",
						email = "support@contentshub.com"
				),
				license = @License(
						name = "Apache 2.0",
						url = "https://www.apache.org/licenses/LICENSE-2.0"
				)
		),
		servers = {
				@Server(url = "http://localhost:8080", description = "Local Development Server"),
				@Server(url = "https://api.contentshub.com", description = "Production Server")
		}
)
@SecurityScheme(
		name = "Bearer Authentication",
		type = SecuritySchemeType.HTTP,
		bearerFormat = "JWT",
		scheme = "bearer"
)
@Slf4j
public class ContentHubApplication {

	public static void main(String[] args) {
		// Configurar timezone por defecto
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		// Configurar propiedades del sistema
		System.setProperty("reactor.netty.http.server.accessLogEnabled", "true");
		System.setProperty("spring.reactor.debug", "true");

		SpringApplication.run(ContentHubApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady(ApplicationReadyEvent event) {
		Environment env = event.getApplicationContext().getEnvironment();
		String protocol = "http";
		if (env.getProperty("server.ssl.key-store") != null) {
			protocol = "https";
		}

		String serverPort = env.getProperty("server.port", "8080");
		String contextPath = env.getProperty("server.servlet.context-path", "/");
		String hostAddress = "localhost";

		try {
			hostAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("No se pudo determinar la dirección del host, usando localhost");
		}

		String applicationName = env.getProperty("spring.application.name", "RealTime Content Hub");
		String activeProfile = String.join(",", env.getActiveProfiles());

		log.info("\n----------------------------------------------------------\n\t" +
						"🚀 '{}' está corriendo! \n\t" +
						"🕐 Hora de inicio: {}\n\t" +
						"📍 URLs de acceso:\n\t" +
						"\t- Local: \t\t{}://localhost:{}{}\n\t" +
						"\t- Externa: \t{}://{}:{}{}\n\t" +
						"\t- Swagger UI: \t{}://localhost:{}/swagger-ui.html\n\t" +
						"\t- API Docs: \t{}://localhost:{}/api-docs\n\t" +
						"\t- Actuator: \t{}://localhost:{}/management/health\n\t" +
						"🔧 Perfil(es): \t{}\n\t" +
						"☕ Java Version: \t{}\n" +
						"----------------------------------------------------------",
				applicationName,
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
				protocol, serverPort, contextPath,
				protocol, hostAddress, serverPort, contextPath,
				protocol, serverPort,
				protocol, serverPort,
				protocol, serverPort,
				activeProfile.isEmpty() ? "default" : activeProfile,
				System.getProperty("java.version")
		);

		// Log de configuraciones importantes
		logImportantConfigurations(env);
	}

	private void logImportantConfigurations(Environment env) {
		log.info("=== Configuraciones Importantes ===");
		log.info("📊 Base de datos PostgreSQL: {}",
				env.getProperty("spring.r2dbc.url", "No configurada"));
		log.info("📚 MongoDB: {}",
				env.getProperty("spring.data.mongodb.uri", "No configurada"));
		log.info("💾 Redis: {}:{}",
				env.getProperty("spring.data.redis.host", "No configurado"),
				env.getProperty("spring.data.redis.port", "6379"));
		log.info("🔐 JWT Expiration: {} ms",
				env.getProperty("spring.security.jwt.expiration", "No configurado"));
		log.info("🌐 CORS Origins: {}",
				env.getProperty("app.cors.allowed-origins", "No configurado"));
		log.info("📁 Content Storage Path: {}",
				env.getProperty("app.content.storage-path", "./uploads"));
		log.info("===================================");
	}

	@Bean
	public String applicationVersion() {
		return getClass().getPackage().getImplementationVersion() != null
				? getClass().getPackage().getImplementationVersion()
				: "0.0.1-SNAPSHOT";
	}
}
