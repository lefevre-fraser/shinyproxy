/*
 * ShinyProxy-Visualizer
 * 
 * Copyright 2021 MetaMorph
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *       
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Support for Internet Explorer
if (typeof String.prototype.startsWith === 'undefined') {
	String.prototype.startsWith = function (str) { return this.indexOf(str) == 0 }
}

function onFail(formGroup) {
	let group = formGroup
	return function (jqXHR, status, error) {
		console.debug(jqXHR)
		console.debug(status)
		console.debug(error)

		if (jqXHR.getResponseHeader("location").startsWith("/login")) {
			console.debug("login redirect")
			createAlertMessage("warning", "You are no longer logged in<br>Please refresh the page")
		} else if (jqXHR.getResponseHeader("location").startsWith("/error")) {
			console.debug("error redirect")
			createAlertMessage("warning", "An error occured while processing your request")
		} else {
			console.debug("unknown error")
			createAlertMessage("danger", "An unknown error has occured<br>Check server logs for detailed logs")
		}

		if (!jqXHR.getResponseHeader("location").startsWith("/login")) {
			if (group) {
				group.classList.add("has-error")
				let errorBlock = group.querySelector(".help-block")
				if (errorBlock) {
					$(errorBlock).html("")
					$(errorBlock).append($(jqXHR.responseText))
					errorBlock.classList.remove("hidden")
				}
			}
		}

		if (group) {
			let button = group.querySelector("button")
			if (button) {
				button.disabled = false;
			}
		}
	}
}

document.addEventListener('DOMContentLoaded', function() {
	let addfileContainer = document.querySelector('#addfile-container')
	if (addfileContainer) {
		addfileContainer.querySelector("#addfile").addEventListener("click", function() {
			let form = addfileContainer.querySelector("#addfile-form")

			let buttonFormGroup = form.querySelector(".form-group:last-of-type")
			buttonFormGroup.disabled = true;
			$.ajax({
				url: "/addfile",
				method: "POST",
				data: new FormData(form),
				processData: false,
				contentType: false,
				dataType: "json",
				context: form
			}).done(function(result) {
				console.debug(result)
				let buttonFormGroup = this.querySelector(".form-group:last-of-type")
				let helpBlock = buttonFormGroup.querySelector(".help-block")
				if (!result.error) {
					window.location.href = "/"
				} else {
					buttonFormGroup.classList.add("has-error")
					helpBlock.classList.remove("hidden")
					helpBlock.innerHTML = result.error
				}
				buttonFormGroup.disabled = false;
			}).fail(onFail(buttonFormGroup))
		})
	}

	let sharefileContainer = document.querySelector('#sharefile-container')
	if (sharefileContainer) {
		sharefileContainer.querySelector("#sharefile").addEventListener("click", function() {
			let form = sharefileContainer.querySelector("#sharefile-form")
			let buttonFormGroup = form.querySelector(".form-group:last-of-type")
			buttonFormGroup.disabled = true;
			$.ajax({
				url: "/sharefile",
				method: "POST",
				data: new FormData(form),
				processData: false,
				contentType: false,
				dataType: "json",
				context: form
			}).done(function(result, status, jqXHR) {
				console.debug(jqXHR)
				console.debug(status)
				console.debug(result)
				let buttonFormGroup = this.querySelector(".form-group:last-of-type")
				let helpBlock = buttonFormGroup.querySelector(".help-block")
				if (!result.error) {
					window.location.href = "/edit"
				} else {
					buttonFormGroup.classList.add("has-error")
					helpBlock.classList.remove("hidden")
					helpBlock.innerHTML = result.error
				}
				buttonFormGroup.disabled = false;
			}).fail(onFail(buttonFormGroup))
		})
	}

	let appList = document.querySelector('#applist')
	if (appList) {
		$("button[data-href]").on("click", function() {
			$.ajax({
				url: this.getAttribute("data-href"),
				method: this.getAttribute("data-href-method"),
				dataType: "json"
			}).done(function(result, status, jqXHR) {
				window.location.replace("/edit")
			}).fail(onFail(null))
		})
	}

}, false);