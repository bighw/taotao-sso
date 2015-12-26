package com.taotao.sso.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.taotao.sso.mapper.UserMapper;
import com.taotao.sso.pojo.User;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    private static Map<Integer, String> TYPES = new HashMap<Integer, String>(3);

    static {
        TYPES.put(1, "username");
        TYPES.put(2, "phone");
        TYPES.put(3, "email");
    }

    public User checkUser(String param, Integer type) throws Exception {
        // 校验type合法性
        if (!TYPES.containsKey(type)) {
            throw new Exception("参数不合法，type只能是1,2,3");
        }
        // 数据库校验
        User user = this.userMapper.queryUser(TYPES.get(type), param);
        return user;
    }

    public void saveUser(User user) {
        // TODO 校验数据是否可用

        this.userMapper.saveUser(user);
    }
}
