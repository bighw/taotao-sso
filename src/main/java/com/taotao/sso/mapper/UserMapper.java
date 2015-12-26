package com.taotao.sso.mapper;

import org.apache.ibatis.annotations.Param;

import com.taotao.sso.pojo.User;

public interface UserMapper {

    /**
     * 查询用户数据
     * @param field 字段
     * @param param 字段值
     * @return
     */
    User queryUser(@Param("field") String field, @Param("param") String param);

    /**
     * 新增用户
     * 
     * @param user
     */
    void saveUser(User user);

}
