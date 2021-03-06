/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.smarthome.io.rest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

/**
 * Static helper methods to build up JSON-like Response objects and error handling.
 *
 * @author Joerg Plewe - initial contribution
 * @author Henning Treu - Provide streaming capabilities
 */
public class JSONResponse {

    private final Logger logger = LoggerFactory.getLogger(JSONResponse.class);

    private static final JSONResponse INSTANCE = new JSONResponse();
    private final Gson gson = new GsonBuilder().setDateFormat(DateTimeType.DATE_PATTERN_WITH_TZ_AND_MS).create();

    static final String JSON_KEY_ERROR_MESSAGE = "message";
    static final String JSON_KEY_ERROR = "error";
    static final String JSON_KEY_HTTPCODE = "http-code";
    static final String JSON_KEY_ENTITY = "entity";

    /**
     * avoid instantiation apart from {@link #createResponse}.
     */
    private JSONResponse() {
    }

    /**
     * in case of error (404 and such)
     *
     * @param status
     * @param errormessage
     * @return Response containing a status and the errormessage in JSON format
     */
    public static Response createErrorResponse(Response.Status status, String errormessage) {
        return createResponse(status, null, errormessage);
    }

    /**
     * Depending on the status, create a Response object containing either the entity alone or an error JSON
     * which might hold the entity as well.
     *
     * @param status the {@link Response.Status} for the response.
     * @param entity the entity which is transformed into a JSON stream.
     * @param errormessage an optional error message (may be null), ignored if the status family is successful
     * @return Response configure for error or success
     */
    public static Response createResponse(Response.Status status, Object entity, String errormessage) {
        if (status.getFamily() != Response.Status.Family.SUCCESSFUL) {
            return INSTANCE.createErrorResponse(status, entity, errormessage);
        }

        return INSTANCE.createResponse(status, entity);
    }

    /**
     * basic configuration of a ResponseBuilder
     *
     * @param status
     * @return ResponseBuilder configured for "Content-Type" MediaType.APPLICATION_JSON
     */
    private ResponseBuilder responseBuilder(Response.Status status) {
        return Response.status(status).header("Content-Type", MediaType.APPLICATION_JSON);
    }

    /**
     * setup JSON depending on the content
     *
     * @param message a message (may be null)
     * @param status
     * @param entity
     * @param ex
     * @return
     */
    private JsonElement createErrorJson(String message, Response.Status status, Object entity, Exception ex) {
        JsonObject resultJson = new JsonObject();
        JsonObject errorJson = new JsonObject();
        resultJson.add(JSON_KEY_ERROR, errorJson);

        errorJson.addProperty(JSON_KEY_ERROR_MESSAGE, message);

        // in case we have a http status code, report it
        if (status != null) {
            errorJson.addProperty(JSON_KEY_HTTPCODE, status.getStatusCode());
        }

        // in case there is an entity...
        if (entity != null) {
            // return the existing object
            resultJson.add(JSON_KEY_ENTITY, gson.toJsonTree(entity));
        }

        // is there an exception?
        if (ex != null) {
            // JSONify the Exception
            JsonObject exceptionJson = new JsonObject();
            exceptionJson.addProperty("class", ex.getClass().getName());
            exceptionJson.addProperty("message", ex.getMessage());
            exceptionJson.addProperty("localized-message", ex.getLocalizedMessage());
            exceptionJson.addProperty("cause", null != ex.getCause() ? ex.getCause().getClass().getName() : null);
            errorJson.add("exception", exceptionJson);
        }

        return resultJson;
    }

    private Response createErrorResponse(Response.Status status, Object entity, String errormessage) {
        ResponseBuilder rp = responseBuilder(status);
        JsonElement errorJson = createErrorJson(errormessage, status, entity, null);
        rp.entity(errorJson);
        return rp.build();
    }

    private Response createResponse(Status status, Object entity) {
        ResponseBuilder rp = responseBuilder(status);

        if (entity == null) {
            return rp.build();
        }

        // The PipedOutputStream will only be closed by the writing thread
        // since closing it during this method call would be too early.
        // The receiver of the response will read from the pipe after this method returns.
        PipedOutputStream out = new PipedOutputStream();

        try {
            // we will not actively close the PipedInputStream since it is read by the receiving end
            // and will be GC'ed once the response is consumed.
            PipedInputStream in = new PipedInputStream(out);
            rp.entity(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Thread writerThread = new Thread(() -> {
            try (JsonWriter jsonWriter = new JsonWriter(new BufferedWriter(new OutputStreamWriter(out)))) {
                if (entity != null) {
                    gson.toJson(entity, entity.getClass(), jsonWriter);
                    jsonWriter.flush();
                }
            } catch (IOException | JsonIOException e) {
                logger.error("Error streaming JSON through PipedInpuStream/PipedOutputStream: ", e);
            }
        });

        writerThread.setDaemon(true); // daemonize thread to permit the JVM shutdown even if we stream JSON.
        writerThread.start();

        return rp.build();
    }

    /**
     * trap exceptions
     *
     * @author Joerg Plewe
     */
    @Provider
    public static class ExceptionMapper implements javax.ws.rs.ext.ExceptionMapper<Exception> {

        private final Logger logger = LoggerFactory.getLogger(ExceptionMapper.class);

        /**
         * create JSON Response
         */

        @Override
        public Response toResponse(Exception e) {
            logger.debug("exception during REST Handling", e);

            Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;

            // in case the Exception is a WebApplicationException, it already carries a Status
            if (e instanceof WebApplicationException) {
                status = (Response.Status) ((WebApplicationException) e).getResponse().getStatusInfo();
            }

            JsonElement ret = INSTANCE.createErrorJson(e.getMessage(), status, null, e);
            return INSTANCE.responseBuilder(status).entity(INSTANCE.gson.toJson(ret)).build();
        }
    }
}
