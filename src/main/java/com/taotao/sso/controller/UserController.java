package com.taotao.sso.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taotao.common.service.RedisService;
import com.taotao.sso.pojo.User;
import com.taotao.sso.service.UserService;

@RequestMapping("user")
@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisService redisService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TICKET = "TICKET_";
    
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 监测数据是否可用
     * 
     * @param param
     * @param type
     * @return
     */
    @RequestMapping(value = "check/{param}/{type}", method = RequestMethod.GET)
    public ResponseEntity<Boolean> checkData(@PathVariable("param") String param,
            @PathVariable("type") Integer type) {
        try {
            User user = this.userService.checkUser(param, type);
            if (user == null) {
                return ResponseEntity.ok(true);
            }
            return ResponseEntity.ok(false);
        } catch (Exception e) {
            e.printStackTrace();
            // 400
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    /**
     * 注册
     * 
     * @param user
     * @param result
     * @param response
     * @return
     */
    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> saveUser(@Valid User user, BindingResult result,
            HttpServletResponse response) {
        if (result.hasErrors()) {
            // 错误处理
            List<String> msgs = new ArrayList<String>();
            List<ObjectError> errors = result.getAllErrors();
            for (ObjectError objectError : errors) {
                msgs.add(objectError.getDefaultMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(StringUtils.join(msgs, " | "));
        }

        try {
            // 把密码做MD5加密
            user.setPassword(DigestUtils.md5Hex(user.getPassword()));
            this.userService.saveUser(user);
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 500
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }

    /**
     * 登录
     * 
     * @param username
     * @param passwd
     * @return
     */
    @RequestMapping(value = "login", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> login(@RequestParam("u") String username,
            @RequestParam("p") String passwd,@RequestParam("data")String data) {
        try {
            Map<String, Object> result = new HashMap<String, Object>();
            User user = this.userService.checkUser(username, 1);
            if (user == null) {
                // 用户名错误
                result.put("msg", "用户名不存在!");
                return ResponseEntity.ok(result);
            }
            // 对比密码是否相同
            if (!StringUtils.equals(user.getPassword(), DigestUtils.md5Hex(passwd))) {
                // 用户名密码错误
                result.put("msg", "用户名或密码错误!");
                return ResponseEntity.ok(result);
            }
            // 将用户数据保存到redis中
            String ticket = DigestUtils.md5Hex(username + System.currentTimeMillis());
            String value = MAPPER.writeValueAsString(user);
            this.redisService.set(TICKET + ticket, value, 60 * 30);
            result.put("msg", "ok");
            result.put("ticket", ticket);
            
            //发送消息通知其他系统该用户已经登录
            Map<String, Object> msg = new HashMap<String, Object>(2);
            msg.put("userId", user.getId());
            msg.put("data", data);
            this.rabbitTemplate.convertAndSend(MAPPER.writeValueAsString(msg));
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }

    /**
     * 查询用户信息
     * 
     * @param ticket
     * @return
     */
    @RequestMapping(value = "{ticket}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<User> queryUserByTicket(@PathVariable("ticket") String ticket) {
        try {
            String key = TICKET + ticket;
            String value = this.redisService.get(key);
            if (StringUtils.isBlank(value)) {
                return ResponseEntity.ok(null);
            }
            User user = MAPPER.readValue(value, User.class);

            // 刷新reds中的数据的生存时间
            this.redisService.expire(key, 60 * 30);

            return ResponseEntity.ok(user);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }

}
