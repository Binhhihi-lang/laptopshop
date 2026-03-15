<%@ page contentType="text/html;charset=UTF-8" language="java" %>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
        <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            <!DOCTYPE html>
            <html lang="en">

            <head>
                <meta charset="utf-8" />
                <meta http-equiv="X-UA-Compatible" content="IE=edge" />
                <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
                <meta name="description" content="" />
                <meta name="author" content="" />
                <title>Detail Info User</title>
                <link href="/css/styles.css" rel="stylesheet" />
                <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js"></script>
                <script>
                    $(document).ready(() =>{
                        // lay thuoc tinh avatarFile
                        const avatarFile = $("#avatarFile");
                        // thay doi file
                        avatarFile.change(function (e) {
                            // lay dg link URL hien thi anh
                            const imgURL = URL.createObjectURL(e.target.files[0]);
                            $("#avatarPreview").attr("src", imgURL);
                            $("#avatarPreview").css({"display": "block"});
                        });
                    });
                </script>
                <script src="https://use.fontawesome.com/releases/v6.3.0/js/all.js" crossorigin="anonymous"></script>
                <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.2.3/dist/js/bootstrap.bundle.min.js"
                    crossorigin="anonymous"></script>
                <script src="js/scripts.js"></script>
            </head>

            <body class="sb-nav-fixed">
                <jsp:include page="../layout/header.jsp" />

                <div id="layoutSidenav">
                    <jsp:include page="../layout/sidebar.jsp" />
                    <div id="layoutSidenav_content">
                        <main>
                            <div class="container-fluid px-4">
                                <h1 class="mt-4">Manage Users</h1>
                                <ol class="breadcrumb mb-4">
                                    <li class="breadcrumb-item"><a href="/admin">Dashboard</a></li>
                                    <li class="breadcrumb-item active">Users</li>
                                </ol>
                                <div class="mt-5">
                                    <div class="row">
                                        <div class="col-md-6 col-12 mx-auto">
                                            <h3>Create User</h3>
                                            <hr>
                                            <form:form method="post" action="/admin/user/create"
                                                modelAttribute="newUser" enctype="multipart/form-data" class="row">
                                                <div class="mb-3 col-12 col-md-6">
                                                    <label for="exampleInputEmail1">Email: </label>
                                                    <form:input type="email" name="email" class="form-control"
                                                        id="emailInput" placeholder="Enter email" path="email" />
                                                </div>

                                                <div class="mb-3 col-12 col-md-6">
                                                    <label for="exampleInputPassword1">Password:</label>
                                                    <form:input type="password" name="password" class="form-control"
                                                        id="passwordInput" placeholder="Password" path="password" />
                                                </div>

                                                <div class="mb-3 col-12 col-md-6">
                                                    <label for="exampleInputEmail1">Phone number: </label>
                                                    <form:input type="text" name="phone" class="form-control"
                                                        id="phoneInput" placeholder="Enter phone /number"
                                                        path="phone" />
                                                </div>

                                                <div class="mb-3 col-12 col-md-6">
                                                    <label for="exampleInputEmail1">Full Name: </label>
                                                    <form:input type="text" name="fullName" class="form-control"
                                                        id="fullNameInput" placeholder="Enter full name"
                                                        path="fullName" />
                                                </div>

                                                <div class="mb-3 col-12">
                                                    <label for="exampleInputEmail1">Address: </label>
                                                    <form:input type="text" name="address" class="form-control"
                                                        id="addressInput" placeholder="Enter address" path="address" />
                                                </div>

                                                <div class="mb-3 col-12 col-md-6">
                                                    <label class="form-label">Role:</label>
                                                    <form:select class="form-select" id="inputGroupSelect01" path="role.name">
                                                        <form:option value="ADMIN">ADMIN</form:option>
                                                        <form:option value="USER">USER</form:option>
                                                    </form:select>
                                                </div>

                                                <!-- Upload file   -->
                                                <div class="mb-3 col-12 col-md-6">
                                                    <label class="form-label" for="avatarFile">Avatar:</label>
                                                    <input type="file" class="form-control" id="avatarFile" name="inputFile" accept=".png, .jpg, .jpeg">
                                                </div>

                                                 <!-- Image với Preview-->
                                                 <div class="col-12 mb-3">
                                                    <img style="max-height: 250px; display: none;" alt="avatar preview" id="avatarPreview">
                                                 </div>

                                                <div class="col-12 mb-5">
                                                    <button type="submit" class="btn btn-primary">Create</button>
                                                </div>
                                            </form:form>
                                        </div>
                                    </div>


                                </div>
                            </div>
                        </main>
                        <jsp:include page="../layout/footer.jsp" />
                    </div>
                </div>
            </body>