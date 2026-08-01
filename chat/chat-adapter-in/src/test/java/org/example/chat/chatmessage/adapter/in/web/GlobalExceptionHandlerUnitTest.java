package org.example.chat.chatmessage.adapter.in.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.example.chat.exception.ChatCacheException;
import org.example.chat.exception.InvalidResourceRequestException;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
import org.example.common.validation.ValidationResult;
import org.example.common.exception.DistributedLockAcquireFailedException;
import org.example.common.exception.DlqNotFoundException;
import org.example.common.exception.ErrorResponse;
import org.example.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerUnitTest {

    @InjectMocks
    private GlobalExceptionHandler sut;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("ConstraintViolationException을 ValidationResult body와 400으로 변환한다")
    void handleConstraintViolationException() {
        // given
        ConstraintViolation violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        ConstraintDescriptor descriptor = mock(ConstraintDescriptor.class);
        NotBlank annotation = mock(NotBlank.class);

        given(violation.getPropertyPath()).willReturn(path);
        given(path.toString()).willReturn("update.roomId");

        given(violation.getConstraintDescriptor()).willReturn(descriptor);
        given(descriptor.getAnnotation()).willReturn(annotation);
        given(annotation.annotationType()).willReturn((Class) NotBlank.class);

        given(violation.getMessage()).willReturn("must not be blank");

        Set violations = Set.of(violation);

        ConstraintViolationException exception = new ConstraintViolationException(violations);

        // when
        ResponseEntity<?> response = sut.handleConstraintViolationException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ValidationResult.class);

        ValidationResult body = (ValidationResult) response.getBody();

        assertThat(body.errors()).hasSize(1);
        assertThat(body.errors().get(0).field()).isEqualTo("roomId");
        assertThat(body.errors().get(0).code()).isEqualTo("NotBlank");
        assertThat(body.errors().get(0).message()).isEqualTo("must not be blank");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException을 ValidationResult body와 400으로 변환한다")
    void handleMethodArgumentNotValidException() {
        // given
        BindingResult bindingResult = new BeanPropertyBindingResult(
                new TestRequest(null),
                "chatRoomCreateRequest"
        );

        bindingResult.addError(new FieldError(
                "chatRoomCreateRequest",
                "title",
                null,
                false,
                new String[]{"NotBlank.chatRoomCreateRequest.title", "NotBlank.title", "NotBlank"},
                null,
                "must not be blank"
        ));

        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(null, bindingResult);

        // when
        ResponseEntity<ValidationResult> response =
                sut.handleMethodArgumentNotValidException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ValidationResult body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.errors()).hasSize(1);
        assertThat(body.errors().get(0).field()).isEqualTo("title");
        assertThat(body.errors().get(0).code()).isEqualTo("NotBlank");
        assertThat(body.errors().get(0).message()).isEqualTo("must not be blank");
    }

    @Test
    @DisplayName("BindException을 ValidationResult body와 400으로 변환한다")
    void handleBindException() {
        // given
        BindException exception = new BindException(
                new TestRequest(null),
                "chatRoomCreateRequest"
        );

        exception.addError(new FieldError(
                "chatRoomCreateRequest",
                "description",
                null,
                false,
                new String[]{"NotBlank.chatRoomCreateRequest.description", "NotBlank.description", "NotBlank"},
                null,
                "must not be blank"
        ));

        // when
        ResponseEntity<ValidationResult> response =
                sut.handleMethodArgumentNotValidException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ValidationResult body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.errors()).hasSize(1);
        assertThat(body.errors().get(0).field()).isEqualTo("description");
        assertThat(body.errors().get(0).code()).isEqualTo("NotBlank");
        assertThat(body.errors().get(0).message()).isEqualTo("must not be blank");
    }

    @Test
    @DisplayName("ChatRoomNotFoundException을 204 No Content로 변환한다")
    void handleChatRoomNotFoundException() {
        // given
        ChatRoomNotFoundException exception = new ChatRoomNotFoundException("room123");

        // when
        ResponseEntity<Void> response = sut.handleResourceNotFoundException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("DlqNotFoundException을 204 No Content로 변환한다")
    void handleDlqNotFoundException() {
        // given
        DlqNotFoundException exception = new DlqNotFoundException("dlq123");

        // when
        ResponseEntity<Void> response = sut.handleResourceNotFoundException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("InvalidResourceRequestException을 ErrorResponse body와 404로 변환한다")
    void handleInvalidResourceRequestException() {
        // given
        InvalidResourceRequestException exception = new InvalidResourceRequestException("이상한 요청입니다");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleInvalidRequestException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("이상한 요청입니다");
    }

    @Test
    @DisplayName("ChatCacheException을 ErrorResponse body와 500으로 변환한다")
    void handleChatCacheException() {
        // given
        ChatCacheException exception = new ChatCacheException("cache error");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleInfrastructureException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).contains("서비스 이용이 원활하지 않습니다");
    }

    @Test
    @DisplayName("DistributedLockAcquireFailedException을 ErrorResponse body와 503으로 변환한다")
    void handleDistributedLockAcquireFailedException() {
        // given
        DistributedLockAcquireFailedException exception = new DistributedLockAcquireFailedException("lock123");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleInfrastructureException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).contains("서비스 이용이 원활하지 않습니다");
    }

    @Test
    @DisplayName("IllegalArgumentException을 ErrorResponse body와 400으로 변환한다")
    void handleIllegalArgumentException() {
        // given
        IllegalArgumentException exception = new IllegalArgumentException("invalid argument");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleBadRequestException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("invalid argument");
    }

    @Test
    @DisplayName("일반 Exception을 ErrorResponse body와 500으로 변환한다")
    void handleException() {
        // given
        Exception exception = new Exception("unexpected error");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("서버 내부 오류가 발생했습니다");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException을 400으로 변환한다")
    void handleHttpMessageNotReadableException() {
        // given
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

        // when
        ResponseEntity<ErrorResponse> response = sut.handleHttpMessageNotReadableException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("잘못된 요청 형식입니다");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException을 400으로 변환한다")
    void handleMissingServletRequestParameterException() {
        // given
        MissingServletRequestParameterException exception = new MissingServletRequestParameterException("param1", "String");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleMissingServletRequestParameterException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("param1");
    }

    @Test
    @DisplayName("MissingRequestHeaderException을 400으로 변환한다")
    void handleMissingRequestHeaderException() {
        // given
        MissingRequestHeaderException exception = mock(MissingRequestHeaderException.class);
        given(exception.getHeaderName()).willReturn("header1");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleMissingRequestHeaderException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("header1");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException을 400으로 변환한다")
    void handleMethodArgumentTypeMismatchException() {
        // given
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        given(exception.getName()).willReturn("param1");

        // when
        ResponseEntity<ErrorResponse> response = sut.handleMethodArgumentTypeMismatchException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("param1");
    }

    private record TestRequest(String title) {
    }
}