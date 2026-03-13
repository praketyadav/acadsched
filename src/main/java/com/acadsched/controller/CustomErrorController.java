package com.acadsched.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute("javax.servlet.error.status_code");
        Object exception = request.getAttribute("javax.servlet.error.exception");
        Object message = request.getAttribute("javax.servlet.error.message");

        int statusCode = status != null ? Integer.parseInt(status.toString()) : 0;

        model.addAttribute("statusCode", statusCode);
        model.addAttribute("message", message != null ? message.toString() : "Unknown Error");
        model.addAttribute("exception", exception);

        return "error";
    }

    public String getErrorPath() {
        return "/error";
    }
}
