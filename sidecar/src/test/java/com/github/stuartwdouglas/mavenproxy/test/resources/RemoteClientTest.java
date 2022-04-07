package com.github.stuartwdouglas.mavenproxy.test.resources;

import static org.hamcrest.CoreMatchers.is;

import javax.ws.rs.WebApplicationException;

import com.github.stuartwdouglas.mavenproxy.resources.MavenResourceManager;
import com.github.stuartwdouglas.mavenproxy.resources.RemoteClient;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;

@QuarkusTest
@TestHTTPEndpoint(MavenResourceManager.class)
public class RemoteClientTest {

    @InjectMock
    RemoteClient remoteClient;

    @Test
    public void testFetchingAJar() {

        Mockito.when(remoteClient.get("default", "io/quarkus", "quarkus-jaxb",
                "2.7.5.Final",
                "quarkus-jaxb-2.7.5.Final.jar")).thenReturn("ajar".getBytes());

        RestAssured.given()
                .when().get("/io/quarkus/quarkus-jaxb/2.7.5.Final/quarkus-jaxb-2.7.5.Final.jar")
                .then()
                .statusCode(200)
                .body(is("ajar"));
    }

    @Test
    public void testFetchingNonExistingJar() {

        Mockito.when(remoteClient.get("default", "io/quarkus", "quarkus-jaxb",
                "2.7.5555555.Final",
                "quarkus-jaxb-2.7.5555555.Final.jar")).thenThrow(new WebApplicationException("Not there", 404));

        RestAssured.given()
                .when().get("/io/quarkus/quarkus-jaxb/2.7.5555555.Final/quarkus-jaxb-2.7.5555555.Final.jar")
                .then()
                .statusCode(404);
    }
}
