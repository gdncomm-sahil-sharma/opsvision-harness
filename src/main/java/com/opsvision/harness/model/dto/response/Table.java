package com.opsvision.harness.model.dto.response;

import java.util.List;

public class Table {
    private String title;
    private List<String> headers;
    private List<List<Object>> data;

    public Table() {}

    public Table(String title, List<String> headers, List<List<Object>> data) {
        this.title = title;
        this.headers = headers;
        this.data = data;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<List<Object>> getData() {
        return data;
    }

    public void setData(List<List<Object>> data) {
        this.data = data;
    }
}