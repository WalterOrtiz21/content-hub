package com.contentshub.application.port.input;

import com.contentshub.domain.model.User;
import reactor.core.publisher.Mono;

public interface RegisterUserUseCase {

    record RegisterUserRequest(String username, String password, String email) {}

    Mono<User> execute(RegisterUserRequest request);
}
