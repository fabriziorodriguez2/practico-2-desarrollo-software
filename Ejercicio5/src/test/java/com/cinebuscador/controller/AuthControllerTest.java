package com.cinebuscador.controller;

import com.cinebuscador.model.User;
import com.cinebuscador.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.ui.ExtendedModelMap;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private UserRepository repositorio;
    private AuthController controlador;

    @BeforeEach
    void preparar() {
        repositorio = mock(UserRepository.class);
        controlador = new AuthController(repositorio);
    }

    @Test
    void registroGuardaUnHashBCryptValido() {
        controlador.register("ana", "Clave123!", "Clave123!", new ExtendedModelMap());
        ArgumentCaptor<User> usuario = ArgumentCaptor.forClass(User.class);
        verify(repositorio).save(usuario.capture());
        String guardada = usuario.getValue().getPassword();
        assertNotEquals("Clave123!", guardada);
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        assertTrue(bcrypt.matches("Clave123!", guardada));
        assertFalse(bcrypt.matches("OtraClave!", guardada));
    }

    @Test
    void mismaClaveSeGuardaDistintaPorElSalt() {
        controlador.register("ana", "Clave123!", "Clave123!", new ExtendedModelMap());
        controlador.register("juan", "Clave123!", "Clave123!", new ExtendedModelMap());
        ArgumentCaptor<User> usuarios = ArgumentCaptor.forClass(User.class);
        verify(repositorio, times(2)).save(usuarios.capture());
        assertNotEquals(usuarios.getAllValues().get(0).getPassword(),
                        usuarios.getAllValues().get(1).getPassword());
    }

    @Test
    void loginAceptaLaClaveCorrectaYRechazaOtra() {
        controlador.register("ana", "Clave123!", "Clave123!", new ExtendedModelMap());
        ArgumentCaptor<User> usuario = ArgumentCaptor.forClass(User.class);
        verify(repositorio).save(usuario.capture());
        when(repositorio.findByUsername("ana")).thenReturn(Optional.of(usuario.getValue()));
        ExtendedModelMap correcto = new ExtendedModelMap();
        ExtendedModelMap incorrecto = new ExtendedModelMap();
        controlador.login("ana", "Clave123!", correcto);
        controlador.login("ana", "OtraClave!", incorrecto);
        assertEquals(true, correcto.get("loginSuccess"));
        assertFalse(incorrecto.containsAttribute("loginSuccess"));
        assertTrue(incorrecto.containsAttribute("loginError"));
    }

    @Test
    void registroYLoginNoExponenLaClaveAlModelo() {
        ExtendedModelMap registro = new ExtendedModelMap();
        controlador.register("ana", "Clave123!", "Clave123!", registro);
        ArgumentCaptor<User> usuario = ArgumentCaptor.forClass(User.class);
        verify(repositorio).save(usuario.capture());
        when(repositorio.findByUsername("ana")).thenReturn(Optional.of(usuario.getValue()));
        ExtendedModelMap login = new ExtendedModelMap();
        controlador.login("ana", "Clave123!", login);
        assertEquals(true, registro.get("registerSuccess"));
        assertEquals(true, login.get("loginSuccess"));
        assertFalse(registro.containsAttribute("encryptedPassword"));
        assertFalse(login.containsAttribute("encryptedPassword"));
    }
}
