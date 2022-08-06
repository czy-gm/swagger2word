package org.word.controller;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.word.service.WordService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.*;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Created by XiuYin.Cui on 2018/1/11.
 */
@Controller
@Slf4j
public class WordController {

    @Value("${swagger.url}")
    private String swaggerUrl;

    @Resource
    private WordService tableService;
    @Resource
    private SpringTemplateEngine springTemplateEngine;

    private String fileName = "toWord";

    /**
     * 将 swagger json文件转换成 word文档并下载
     *
     * @param model
     * @param jsonFile 需要转换成 word 文档的swagger json文件
     * @param response
     * @return
     */
    @ApiOperation(value = "将 swagger json文件转换成 word文档并下载", notes = "", tags = {"Word"})
    @ApiResponses(value = {@ApiResponse(code = 200, message = "请求成功。")})
    @RequestMapping(value = "/fileToWord", method = {RequestMethod.POST})
    public void getWord(Model model, @ApiParam("swagger json file") @Valid @RequestPart("jsonFile") MultipartFile jsonFile, HttpServletResponse response) {
        generateModelData(model, jsonFile);
        writeContentToResponse(model, response);
    }

    /**
     * 将 swagger 文档一键下载为 doc 文档
     *
     * @param model
     * @param url      需要转换成 word 文档的资源地址
     * @param response
     */
    @ApiOperation(value = "将 swagger 文档一键下载为 doc 文档", notes = "", tags = {"Word"})
    @ApiResponses(value = {@ApiResponse(code = 200, message = "请求成功。")})
    @RequestMapping(value = "/downloadWord", method = {RequestMethod.GET})
    public void word(Model model, @ApiParam(value = "资源地址", required = false) @RequestParam(required = false) String url, HttpServletResponse response) {
        generateModelData(model, url, 0);
        writeContentToResponse(model, response);
    }

    private void generateModelData(Model model, MultipartFile jsonFile) {
        Map<String, Object> result = tableService.tableList(jsonFile);
        fileName = jsonFile.getOriginalFilename();

        if (fileName != null) {
            fileName = fileName.replaceAll(".json", "");
        } else {
            fileName = "toWord";
        }

        model.addAttribute("url", "http://");
        model.addAttribute("download", 0);
        model.addAllAttributes(result);
    }

    private void generateModelData(Model model, String url, Integer download) {
        url = StringUtils.defaultIfBlank(url, swaggerUrl);
        Map<String, Object> result = tableService.tableList(url);
        model.addAttribute("url", url);
        model.addAttribute("download", download);
        model.addAllAttributes(result);
    }

    private void writeContentToResponse(Model model, HttpServletResponse response) {
        Context context = new Context();
        context.setVariables(model.asMap());
        String content = springTemplateEngine.process("word", context);
        response.setContentType("application/octet-stream;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName + ".doc", "utf-8"));
            byte[] bytes = content.getBytes();
            bos.write(bytes, 0, bytes.length);
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
