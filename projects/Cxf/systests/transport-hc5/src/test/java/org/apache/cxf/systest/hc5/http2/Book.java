/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.systest.hc5.http2;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.CLASS, include = As.PROPERTY, property = "class")
@XmlRootElement(name = "Book")
public class Book {
    private String name;
    private long id;

    public Book() {
    }

    public Book(String name, long id) {
        this.name = name;
        this.id = id;
    }

    public void setName(String n) {
        name = n;
    }

    public String getName() {
        return name;
    }

    public void setId(long i) {
        id = i;
    }
    public long getId() {
        return id;
    }

    @PUT
    public void cloneState(Book book) {
        id = book.getId();
        name = book.getName();
    }

    @GET
    public Book retrieveState() {
        return this;
    }
}
