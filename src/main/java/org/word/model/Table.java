package org.word.model;

import lombok.Data;

import java.util.List;

/**
 * Created by XiuYin.Cui on 2018/1/11.
 */
@Data
public class Table {

    public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getRequestForm() {
		return requestForm;
	}

	public void setRequestForm(String requestForm) {
		this.requestForm = requestForm;
	}

	public String getResponseForm() {
		return responseForm;
	}

	public void setResponseForm(String responseForm) {
		this.responseForm = responseForm;
	}

	public String getRequestType() {
		return requestType;
	}

	public void setRequestType(String requestType) {
		this.requestType = requestType;
	}

	public List<Request> getPathList() {
		return pathList;
	}

	public void setPathList(List<Request> pathList) {
		this.pathList = pathList;
	}

	public List<Request> getQueryList() {
		return queryList;
	}

	public void setQueryList(List<Request> queryList) {
		this.queryList = queryList;
	}

	public List<Request> getBodyList() {
		return bodyList;
	}

	public void setBodyList(List<Request> bodyList) {
		this.bodyList = bodyList;
	}

	public List<Response> getResponseList() {
		return responseList;
	}

	public void setResponseList(List<Response> responseList) {
		this.responseList = responseList;
	}

	public String getRequestParam() {
		return requestParam;
	}

	public void setRequestParam(String requestParam) {
		this.requestParam = requestParam;
	}

	public String getResponseParam() {
		return responseParam;
	}

	public void setResponseParam(String responseParam) {
		this.responseParam = responseParam;
	}

	public ModelAttr getModelAttr() {
		return modelAttr;
	}

	public void setModelAttr(ModelAttr modelAttr) {
		this.modelAttr = modelAttr;
	}

	/**
     * ?????????
     */
    private String title;
    /**
     * ?????????
     */
    private String tag;
    /**
     * url
     */
    private String url;

    /**
     * ??????
     */
    private String description;

    /**
     * ??????????????????
     */
    private String requestForm;

    /**
     * ??????????????????
     */
    private String responseForm;

    /**
     * ????????????
     */
    private String requestType;

    /**
     * ?????????
     */
    private List<Request> pathList;

    private List<Request> queryList;

    private List<Request> bodyList;

    /**
     * ?????????
     */
    private List<Response> responseList;

    /**
     * ????????????
     */
    private String requestParam;

    /**
     * ????????????
     */
    private String responseParam;

    /**
     * ??????????????????
     */
    private ModelAttr modelAttr = new ModelAttr();
}
