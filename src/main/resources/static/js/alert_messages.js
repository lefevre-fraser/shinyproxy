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
function createAlertMessage(type, message) {
    let alertMessages = document.querySelector("#alert_messages")
    let alert = null

    if (alertMessages) {
        switch (type.toLowerCase()) {
            case "success":
                alert = alertMessages.querySelector("template#success_alert")
                break;

            case "info":
                alert = alertMessages.querySelector("template#info_alert")
                break;

            case "warning":
                alert = alertMessages.querySelector("template#warning_alert")
                break;

            case "danger":
                alert = alertMessages.querySelector("template#danger_alert")
                break;

            default:
                break;
        }
        alert = $($(alert).html())
        $(alert).children("span").append(message)
        $(alertMessages).append(alert.get(0))
    }

    return alert
}