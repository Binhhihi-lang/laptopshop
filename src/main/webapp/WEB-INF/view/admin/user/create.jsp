<%@ page contentType="text/html;charset=UTF-8" language="java" %>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
        <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

            <html lang="en">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Document</title>
                <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
                <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
                <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js"></script>
            </head>

            <body>
                <div class="container mt-5">
                    <div class="row">
                        <div class="col-md-6 col-12 mx-auto">
                            <h3>Create User</h3>
                            <hr>
                            <form:form method="post" action="/admin/user/create1" modelAttribute="newUser">
                                <div class="mb-3">
                                    <label for="exampleInputEmail1">Email: </label>
                                    <form:input type="email" name="email" class="form-control" id="emailInput"
                                        placeholder="Enter email" path="email" />
                                </div>

                                <div class="mb-3">
                                    <label for="exampleInputPassword1">Password:</label>
                                    <form:input type="password" name="password" class="form-control" id="passwordInput"
                                        placeholder="Password" path="password" />
                                </div>

                                <div class="mb-3">
                                    <label for="exampleInputEmail1">Phone number: </label>
                                    <form:input type="text" name="phone" class="form-control" id="phoneInput"
                                        placeholder="Enter phone /number" path="phone" />
                                </div>


                                <div class="mb-3">
                                    <label for="exampleInputEmail1">Full Name: </label>
                                    <form:input type="text" name="fullName" class="form-control" id="fullNameInput"
                                        placeholder="Enter full name" path="fullName" />
                                </div>


                                <div class="mb-3">
                                    <label for="exampleInputEmail1">Address: </label>
                                    <form:input type="text" name="address" class="form-control" id="addressInput"
                                        placeholder="Enter address" path="address" />
                                </div>


                                <button type="submit" class="btn btn-primary">Create</button>
                            </form:form>
                        </div>
                    </div>


                </div>

            </body>

            </html>