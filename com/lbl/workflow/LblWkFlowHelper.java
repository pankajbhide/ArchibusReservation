package com.lbl.workflow;


import java.util.*;
import org.apache.log4j.Logger;


import com.archibus.datasource.*;

import com.archibus.datasource.data.*;
import com.archibus.datasource.restriction.*;
import com.archibus.datasource.restriction.Restrictions.Restriction;
import com.archibus.schema.*;
import com.archibus.utility.*;

public class LblWkFlowHelper {

	/*************************************************************
     * Method to find out whether the email is valid or not
     *
     **************************************************************/
    public String isEmailValid(String strEmail)
    {
     	
      String strReturn=null;
      strReturn=findEmpVisitor(strEmail,"Employee");
      if (!strReturn.equals("Notfound"))  return strReturn;
      strReturn=findEmpVisitor(strEmail,"Visitor");
      return strReturn;
    }
    
     // Look into employee/visitor 
     private String findEmpVisitor(String strEmail, String strType) {
	

	 String strEmailChecked=null;
     DataSource ds = DataSourceFactory.createDataSource();
   	 String strUpperEmail=strEmail.toUpperCase();
     String strWhere=" upper(email)=" +"'" + strUpperEmail + "'";
   	 
   	 // Employee
     if (strType.equals("Employee"))
     { 
    	 ds.addTable("em");
    	 ds.addField("em_number");
         ds.addField("email");
     }
     // Visitor
     if (strType.equals("Visitor"))
     { 
    	 ds.addTable("visitors");
    	 ds.addField("name_last");
         ds.addField("email");
     }
     
   	 ds.addRestriction(Restrictions.sql(strWhere));
   	 List <DataRecord> records=ds.getRecords();

//   	 for (DataRecord record : records) {
//   		 if (strType.equals("Employee"))
//   		  strEmployeeName=record.getString("em.em_number");
//   		if (strType.equals("Visitor"))
//     	  strEmployeeName=record.getString("visitors.name_last");
//   	 }

     for (DataRecord record : records) {
         if (strType.equals("Employee"))
             strEmailChecked=record.getString("em.email");
         if (strType.equals("Visitor"))
             strEmailChecked=record.getString("visitors.email");
     }
     records=null;
     ds=null;
   
   	 if (strEmailChecked != null ) return strEmailChecked;
   	 else
   		 return "Notfound";
     }

   
  }