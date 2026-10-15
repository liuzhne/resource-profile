package com.edu.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE sys_user SET nickname=#{nickname},email=#{email},phone=#{phone},bio=#{bio},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND deleted=0 AND status=1")
    int updateProfile(@Param("id") Long id, @Param("nickname") String nickname, @Param("email") String email,
                      @Param("phone") String phone, @Param("bio") String bio);

    @Update("UPDATE sys_user SET password=#{password},update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND password=#{oldHash} AND deleted=0 AND status=1")
    int changePassword(@Param("id") Long id, @Param("oldHash") String oldHash, @Param("password") String password);

    // Preliminary login lookup/BCrypt runs before the short shared identity coordination lock.
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    User selectByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    User selectByIdForUpdate(Long id);

    @Insert("INSERT INTO sys_login_log(user_id,username,role,ip,login_time,status) " +
            "VALUES(#{userId},#{username},#{role},#{ip},CURRENT_TIMESTAMP,#{status})")
    void recordLogin(@Param("userId") Long userId, @Param("username") String username,
                     @Param("role") String role, @Param("ip") String ip, @Param("status") int status);
}
