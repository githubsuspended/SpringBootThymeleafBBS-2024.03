package com.example.abbs.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.example.abbs.entity.User;
import com.example.abbs.service.UserService;
import com.example.abbs.util.AsideUtil;
import com.example.abbs.util.ImageUtil;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserController {
	private final Logger log = LoggerFactory.getLogger(getClass());
	@Autowired private UserService userService;
	@Autowired private ImageUtil imageUtil;
	@Autowired private AsideUtil asideUtil;
	@Autowired private ResourceLoader resourceLoader;
	@Value("${spring.servlet.multipart.location}") private String uploadDir;

	@GetMapping("/register")
	public String registerForm() {
		return "user/register";
	}
	
	@PostMapping("/register") 
	public String registerProc(MultipartHttpServletRequest req, Model model,
			String uid, String pwd, String pwd2, String uname, String email,
			String github, String insta, String location) {
		String filename = null;
		MultipartFile filePart = req.getFile("profile");
		
		if (userService.getUserByUid(uid) != null) {
			model.addAttribute("msg", "사용자 ID가 중복되었습니다.");
			model.addAttribute("url", "/abbs/user/register");
			return "common/alertMsg";
		}
		if (pwd.equals(pwd2) && pwd != null) {
			if (filePart.getContentType().contains("image")) {
				filename = filePart.getOriginalFilename();
				String path = uploadDir + "profile/" + filename;
				try {
					filePart.transferTo(new File(path));
				} catch (Exception e) {
					e.printStackTrace();
				}
				filename = imageUtil.squareImage(uid, filename);
			}
			User user = new User(uid, pwd, uname, email, filename, github, insta, location);
			userService.registerUser(user);
			model.addAttribute("msg", "등록을 마쳤습니다. 로그인하세요.");
			model.addAttribute("url", "/abbs/user/login");
			return "common/alertMsg";
		} else {
			model.addAttribute("msg", "패스워드 입력이 잘못되었습니다.");
			model.addAttribute("url", "/abbs/user/register");
			return "common/alertMsg";
		}
	}
	
	@GetMapping("/login")
	public String loginForm() {
		return "user/login";
	}
	
	@PostMapping("/login")
	public String loginProc(String uid, String pwd, HttpSession session, Model model) {
		int result = userService.login(uid, pwd);
		switch(result) {
		case UserService.CORRECT_LOGIN:
			User user = userService.getUserByUid(uid);
			session.setAttribute("sessUid", uid);
			session.setAttribute("sessUname", user.getUname());
			session.setAttribute("profile", user.getProfile());
			session.setAttribute("email", user.getEmail());
			session.setAttribute("github", user.getGithub());
			session.setAttribute("insta", user.getInsta());
			session.setAttribute("location", user.getLocation());
			// 상태 메세지
			// resources/static/data/todayQuote.txt
			Resource resource = resourceLoader.getResource("classpath:/static/data/todayQuote.txt");
			String quoteFile = null;
			try {
				quoteFile = resource.getURI().getPath();
			} catch (IOException e) {
				e.printStackTrace();
			}
			String stateMsg = asideUtil.getTodayQuote(quoteFile);
			session.setAttribute("stateMsg", stateMsg);
			// 환영 메세지
			log.info("Info Login: {}, {}", uid, user.getUname());
			model.addAttribute("msg", user.getUname()+"님 환영합니다.");
			model.addAttribute("url", "/abbs/board/list");
			break;
			
		case UserService.USER_NOT_EXIST:
			model.addAttribute("msg", "ID가 없습니다. 회원가입 페이지로 이동합니다.");
			model.addAttribute("url", "/abbs/user/register");
			break;
		
		case UserService.WRONG_PASSWORD:
			model.addAttribute("msg", "패스워드 입력이 잘못되었습니다. 다시 입력하세요.");
			model.addAttribute("url", "/abbs/user/login");
		}
		return "common/alertMsg";
	}
	
	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/user/login";
	}
	
	@GetMapping({"/list/{page}", "/list"})
	public String list(@PathVariable(required=false) Integer page, HttpSession session, Model model) {
		page = (page == null) ? 1 : page;
		session.setAttribute("currentUserPage", page);
		List<User> list = userService.getUserList(page);
		model.addAttribute("userList", list);
		
		// for pagination
		int totalUsers = userService.getUserCount();
		int totalPages = (int) Math.ceil(totalUsers * 1.0 / userService.COUNT_PER_PAGE);
		List<Integer> pageList = new ArrayList<>();
		for (int i = 1; i <= totalPages; i++)
			pageList.add(i);
		model.addAttribute("pageList", pageList);
		
		return "user/list";
	}

	@ResponseBody
	@GetMapping("/detail/{uid}")
	public String detail(@PathVariable String uid) {
		User user = userService.getUserByUid(uid);
		JSONObject jUser = new JSONObject();
		jUser.put("uid", uid);
		jUser.put("pwd", user.getPwd());
		jUser.put("uname", user.getUname());
		jUser.put("email", user.getEmail());
		jUser.put("profile", user.getProfile());
		jUser.put("github", user.getGithub());
		jUser.put("insta", user.getInsta());
		jUser.put("location", user.getLocation());
		return jUser.toString();
	}
	
	@PostMapping("/update")
	public String update(String uid, String pwd, String pwd2, String uname, String email,
			String github, String insta, String location, String hashedPwd, String profile,
			MultipartHttpServletRequest req, HttpSession session, Model model) {
		String filename = null;
		String sessUid = (String) session.getAttribute("sessUid");
		int currentUserPage = (Integer) session.getAttribute("currentUserPage");
		MultipartFile filePart = req.getFile("newProfile");
		if (!uid.equals(sessUid)) {
			model.addAttribute("msg", "수정 권한이 없습니다.");
			model.addAttribute("url", "/abbs/user/list/" + currentUserPage);
			return "common/alertMsg";
		}
		if (pwd != null && pwd.length() > 1 && pwd.equals(pwd2))
			hashedPwd = BCrypt.hashpw(pwd, BCrypt.gensalt());
		if (filePart.getContentType().contains("image")) {
			filename = filePart.getOriginalFilename();
			String path = uploadDir + "profile/" + filename;
			try {
				filePart.transferTo(new File(path));
			} catch (Exception e) {
				e.printStackTrace();
			}
			profile = imageUtil.squareImage(uid, filename);
		}
		User user = new User(uid, hashedPwd, uname, email, profile, github, insta, location);
		userService.updateUser(user);
		session.setAttribute("sessUname", uname);
		session.setAttribute("profile", profile);
		session.setAttribute("email", email);
		session.setAttribute("github", github);
		session.setAttribute("insta", insta);
		session.setAttribute("location", location);
		return "redirect:/user/list/" + currentUserPage;
	}
	
	@GetMapping("/delete/{uid}")
	public String delete(@PathVariable String uid, HttpSession session, Model model) {
		String sessUid = (String) session.getAttribute("sessUid");
		if (sessUid.equals("admin")) {
			userService.deleteUser(uid);
			return "redirect:/user/list";
		} else if (sessUid.equals(uid)) {
			userService.deleteUser(uid);
			session.invalidate();
			return "redirect:/user/login";
		} else {
			model.addAttribute("msg", "삭제 권한이 없습니다.");
			model.addAttribute("url", "/abbs/user/list");
			return "common/alertMsg";
		}
	}
	
}
