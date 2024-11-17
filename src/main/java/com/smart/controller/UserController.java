package com.smart.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.smart.dao.ContactRepository;
import com.smart.dao.UserRepository;
import com.smart.entities.Contact;
import com.smart.entities.User;
import com.smart.helper.Message;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserController {
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private ContactRepository contactRepository;
	
	
	
	//method for adding common data to response
	@ModelAttribute
	public void addCommonData(Model model,Principal principal) {
		String userName = principal.getName();
		System.out.println("USERNAME "+userName);
		
		//get the user using suername(Email)
		
		User user = userRepository.getUserByUserName(userName);
		System.out.println("User "+user);
		
		model.addAttribute("user", user);
	}
	
	// dashbord home
	@RequestMapping("/index")
	public String dashboard(Model model,Principal principal)
	{
		model.addAttribute("title", "User Dashbord");
		return "normal/user_dashboard";
	}
	
	//open add form handler
	@GetMapping("/add-contact")
	public String openAddContactForm(Model model)
	{	
		model.addAttribute("title", "Add Contact");
		model.addAttribute("contact", new Contact());
		
		return "normal/add_contact_form";
	}
	
	//processing Add contact form
	@PostMapping("/process-contact")
	public String processContact(
			@ModelAttribute Contact contact,
			@RequestParam("profileImage") MultipartFile file,
			Principal principal,Model model) {
		try {
			
			String name = principal.getName();
			User user = this.userRepository.getUserByUserName(name);
			
			//processing and uploading file		
			
			if(file.isEmpty()) {
				//if the file is empty then try our message 
				System.out.println("File is empty");
				contact.setImage("contact.png");
			}
			else {
				//file the file to folder and update the name to contact
				contact.setImage(file.getOriginalFilename());
				
				File saveFile = new ClassPathResource("static/img").getFile();
				
				Path path = Paths.get(saveFile.getAbsolutePath()+File.separator+file.getOriginalFilename());
				
				Files.copy(file.getInputStream(),path , StandardCopyOption.REPLACE_EXISTING);
				
				System.out.println("image is uploded");
			}
			
			user.getContacts().add(contact);
			
			contact.setUser(user);
			
			this.userRepository.save(user);
			
			System.out.println("DATA"+contact);
			System.out.println("Added to DataBase");
			
			//message success
			model.addAttribute("message", new Message("Successfully added your contact!!","alert-success"));
		} catch (Exception e) {
			System.out.println("Error"+e.getMessage());
			e.printStackTrace();
			//message error
			model.addAttribute("message", new Message("Some error occured !! please try again","alert-danger"));
		}
		return "normal/add_contact_form" ;
		
	}
	
	//show contact handler
	//per page=5[n]
	//current page=0[page]
	@GetMapping("/show-contacts/{page}")
	public String showContacts(@PathVariable("page") Integer page, Model m,Principal principal) {
		m.addAttribute("title", "Show user Contacts");
		//contact ki list ko bhejani hai
		
		String userName = principal.getName();
		
		User user = this.userRepository.getUserByUserName(userName);
		
		Pageable pageable = PageRequest.of(page,3);
		
		Page<Contact> contacts = this.contactRepository.findContactsByUser(user.getId(),pageable);
		
		m.addAttribute("contacts", contacts);
		m.addAttribute("currentPage",page);
		m.addAttribute("totalPages", contacts.getTotalPages());
		
		return "normal/show_contacts";
	}
	
	//showing particular contact detail
	
	@RequestMapping("/{cId}/contact")
	public String showContactDetail(@PathVariable("cId") Integer cId,Model model,Principal principal) {
		System.out.println("cid "+cId);
		
		Optional<Contact> contactOptional = this.contactRepository.findById(cId);
		Contact contact = contactOptional.get();
		
		//
		String userName = principal.getName();
		User user = this.userRepository.getUserByUserName(userName);
		
		if(user.getId()==contact.getUser().getId()) {
			
			model.addAttribute("contact", contact);
			model.addAttribute("title", contact.getName());
		}
		
		return "normal/contact_detail";
	}
	
	//delete contact handler
	@GetMapping("/delete/{cid}")
	public String deleteContact(@PathVariable("cid") Integer cId,Model model,RedirectAttributes redirectAttributes,Principal principal) {
		
		Optional<Contact> contactOptional = this.contactRepository.findById(cId);
		Contact contact=contactOptional.get();
		
		//check...
		//contact.setUser(null);
		
		//remove
		//img
//		contact.getImage()
		
		
		//this.contactRepository.delete(contact);
		
		User user =this.userRepository.getUserByUserName(principal.getName());
		user.getContacts().remove(contact);
		this.userRepository.save(user);
		
		
		redirectAttributes.addFlashAttribute("message", new Message("Contact deleted succesful...", "alert-success"));
		
		return "redirect:/user/show-contacts/0";
	}
	
	//open update form handler
	@PostMapping("/update-contact/{cid}")
	public String updateForm(@PathVariable("cid") Integer cid, Model m) {
		m.addAttribute("title","update contact");
		
		Contact contact = this.contactRepository.findById(cid).get();
		
		m.addAttribute("contact", contact);
		return "normal/update_form";
	}
	
	//update contact handler
	@RequestMapping(value ="/process-update",method = RequestMethod.POST )
	public String updateHandler(@ModelAttribute Contact contact,@RequestParam("profileImage") MultipartFile file,Model m,RedirectAttributes redirectAttributes,Principal principal)
	{
		try {
			//old contact detail
			Contact oldContactDetail = this.contactRepository.findById(contact.getcId()).get();
			
			//image
			if(!file.isEmpty()) {
				//file work
				//rewrite
				
               //delete old photo
				  File deleteFile = new ClassPathResource("static/img").getFile();
				  File file1=new File(deleteFile,oldContactDetail.getImage());
				  file1.delete();
				  
				
				//update new photo
				
                File saveFile = new ClassPathResource("static/img").getFile();
				
				Path path = Paths.get(saveFile.getAbsolutePath()+File.separator+file.getOriginalFilename());
				
				Files.copy(file.getInputStream(),path , StandardCopyOption.REPLACE_EXISTING);
				
				contact.setImage(file.getOriginalFilename());
				
			}else {
				contact.setImage(oldContactDetail.getImage());
			}
			
			User user=this.userRepository.getUserByUserName(principal.getName());
			contact.setUser(user);
			this.contactRepository.save(contact);
			
			redirectAttributes.addFlashAttribute("message", new Message("your Contact is Updated ...", "alert-success"));
			
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		System.out.println("CONTACT NAME"+contact.getName());
		System.out.println("CONTACT ID"+contact.getcId());
		return "redirect:/user/"+contact.getcId()+"/contact";
	}
	
	//your profile handler
	@GetMapping("/profile")
	public String yourProfile(Model model)
	{
		model.addAttribute("title","Profile page");
		return "normal/profile";
	}
	
	//creating order for payment
//	@PostMapping("/create_order")
//	@ResponseBody
//	public String createOrder(@RequestBody Map<String,Object> data) throws Exception{
//		
//		//System.out.println("hey order function exe");
//		//System.out.println(data);
//	int amt=Integer.parseInt(data.get("amount").toString());
//	var client = new RazorpayClient(null, null);
//	
//	JSONObject ob=new JSONObject();
//	ob.put("amount", amt*100);
//	ob.put("currency", "INR");
//	ob.put("receipt", "txn_123456");
//	
//	//creating new order
//	Order order = client.Orders.create(ob);
//	System.out.println(order);
//	
//	//ifyou want you can save this to your database
//		return order.toString();
//	}
}
