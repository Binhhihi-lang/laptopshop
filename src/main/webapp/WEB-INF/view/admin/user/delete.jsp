<%@ page contentType="text/html;charset=UTF-8" language="java" %>
    <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
        <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

            <html lang="en">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Delete User ${id}</title>
                <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
                <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
                <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js"></script>
            </head>

            <body>
                <div class="container mt-5">
                    <div class="row">
                        <div class="col-12">
                            <h3>Delete the user with id = ${id}</h3>
                            <hr>
                            <div class="alert alert-danger" role="alert">
                                A simple danger alert—check it out!
                            </div>
                            <form:form action="/admin/user/delete" method = "post" modelAttribute = "userDelete">
                                <div class="mb-3" style="display: none;">
                                    <label for="exampleInputId1">Id: </label>
                                    <form:input type="Id" value = "${id}" name="Id" class="form-control" id="IdInput"
                                        placeholder="Enter ID" path="Id"/>
                                </div>
                                <button href="" class="btn btn-danger">Confirm</button>
                            </form:form>
                        </div>
                    </div>
                </div>
            </body>

            </html>